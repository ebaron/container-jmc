/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.cryostat.rules;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.sasl.SaslException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.platform.ServiceRef;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingNotFoundException;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;

class PeriodicArchiver implements Runnable {

    private static final Pattern RECORDING_FILENAME_PATTERN =
            Pattern.compile(
                    "([A-Za-z\\d-]*)_([A-Za-z\\d-_]*)_([\\d]*T[\\d]*Z)(\\.[\\d]+)?(\\.jfr)?");

    private final ServiceRef serviceRef;
    private final CredentialsManager credentialsManager;
    private final Rule rule;
    private final RecordingArchiveHelper recordingArchiveHelper;
    private final Function<Pair<ServiceRef, Rule>, Void> failureNotifier;
    private final Logger logger;
    private final Base32 base32;

    private final Queue<String> previousRecordings;

    PeriodicArchiver(
            ServiceRef serviceRef,
            CredentialsManager credentialsManager,
            Rule rule,
            RecordingArchiveHelper recordingArchiveHelper,
            Function<Pair<ServiceRef, Rule>, Void> failureNotifier,
            Logger logger,
            Base32 base32) {
        this.serviceRef = serviceRef;
        this.credentialsManager = credentialsManager;
        this.recordingArchiveHelper = recordingArchiveHelper;
        this.rule = rule;
        this.failureNotifier = failureNotifier;
        this.logger = logger;
        this.base32 = base32;

        this.previousRecordings = new ArrayDeque<>(this.rule.getPreservedArchives());
    }

    @Override
    public void run() {
        logger.trace("PeriodicArchiver for {} running", rule.getRecordingName());

        try {
            // If there are no previous recordings, either this is the first time this rule is being
            // archived or the Cryostat instance was restarted. Since it could be the latter,
            // populate the array with any previously archived recordings for this rule.
            if (previousRecordings.isEmpty()) {
                String serviceUri = serviceRef.getServiceUri().toString();
                List<ArchivedRecordingInfo> archivedRecordings =
                        recordingArchiveHelper.getRecordings().get();

                for (ArchivedRecordingInfo archivedRecordingInfo : archivedRecordings) {
                    String decodedServiceUri =
                            new String(
                                    base32.decode(archivedRecordingInfo.getEncodedServiceUri()),
                                    StandardCharsets.UTF_8);
                    String fileName = archivedRecordingInfo.getName();
                    Matcher m = RECORDING_FILENAME_PATTERN.matcher(fileName);
                    if (m.matches()) {
                        String recordingName = m.group(2);

                        if (decodedServiceUri.equals(serviceUri)
                                && recordingName.equals(rule.getRecordingName())) {
                            previousRecordings.add(fileName);
                        }
                    }
                }
            }

            while (previousRecordings.size() > rule.getPreservedArchives() - 1) {
                pruneArchive(previousRecordings.remove());
            }

            performArchival();
        } catch (Exception e) {
            logger.error(e);

            if (ExceptionUtils.hasCause(e, ExecutionException.class)
                    || ExceptionUtils.hasCause(e, InterruptedException.class)
                    || ExceptionUtils.hasCause(e, RecordingNotFoundException.class)
                    || ExceptionUtils.hasCause(e, SecurityException.class)
                    || ExceptionUtils.hasCause(e, SaslException.class)
                    || ExceptionUtils.hasCause(e, ArchivePathException.class)) {

                failureNotifier.apply(Pair.of(serviceRef, rule));
            }
        }
    }

    private void performArchival() throws InterruptedException, ExecutionException, Exception {
        String recordingName = rule.getRecordingName();
        ConnectionDescriptor connectionDescriptor =
                new ConnectionDescriptor(serviceRef, credentialsManager.getCredentials(serviceRef));

        ArchivedRecordingInfo archivedRecordingInfo =
                recordingArchiveHelper.saveRecording(connectionDescriptor, recordingName).get();
        previousRecordings.add(archivedRecordingInfo.getName());
    }

    private void pruneArchive(String recordingName) throws Exception {
        recordingArchiveHelper.deleteRecording(recordingName).get();
        previousRecordings.remove(recordingName);
    }

    public Queue<String> getPreviousRecordings() {
        return previousRecordings;
    }
}
