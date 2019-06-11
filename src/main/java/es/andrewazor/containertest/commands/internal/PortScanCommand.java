package es.andrewazor.containertest.commands.internal;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import es.andrewazor.containertest.commands.SerializableCommand;
import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class PortScanCommand implements SerializableCommand {

    private final ClientWriter cw;

    @Inject
    PortScanCommand(ClientWriter cw) {
        this.cw = cw;
    }

    @Override
    public String getName() {
        return "port-scan";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 0) {
            cw.println("No arguments expected");
            return false;
        }
        return true;
    }

    @Override
    public void execute(String[] args) throws Exception {
        scanOSO().forEach(m -> cw.println(String.format("%s -> %s", m.hostname, m.ip)));
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            return new ListOutput<>(scanOSO());
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    private List<IpHostMapping> scan() throws UnknownHostException, InterruptedException {
        List<IpHostMapping> result = new ArrayList<>();
        int threads = 16;
        CountDownLatch latch = new CountDownLatch(254);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        byte[] localAddress = InetAddress.getLocalHost().getAddress();
        for (int i = 1; i <= 254; i++) {
            byte[] remote = new byte[] {
                localAddress[0],
                localAddress[1],
                localAddress[2],
                (byte) i
            };
            executor.submit(() -> {
                try {
                    Socket s = new Socket();
                    InetAddress addr = InetAddress.getByAddress(remote);
                    s.connect(new InetSocketAddress(addr, 9091), 100);
                    s.close();
                    result.add(new IpHostMapping(addr.getHostAddress(), addr.getCanonicalHostName()));
                } catch (IOException ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        return result;
    }
    
    private List<IpHostMapping> scanOSO()  {
        // OpenShift Online doesn't necessarily put all applications on
        // the same /24 subnet. Hardcode demo components as temporary workaround.
        List<IpHostMapping> result = new ArrayList<>();
        final String[] hostnames = { "robotmaker", "robotshop", "robotcontroller" };
        for (String hostname : hostnames) {
            try {
                InetAddress addr = InetAddress.getByName(hostname);
                result.add(new IpHostMapping(addr.getHostAddress(), addr.getCanonicalHostName()));
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    static class IpHostMapping {
        final String ip;
        final String hostname;
        IpHostMapping(String ip, String hostname) {
            this.ip = ip;
            this.hostname = hostname;
        }
    }

}