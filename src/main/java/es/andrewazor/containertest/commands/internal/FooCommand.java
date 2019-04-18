package es.andrewazor.containertest.commands.internal;

import java.lang.management.MemoryMXBean;
import java.lang.reflect.Method;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import com.sun.management.OperatingSystemMXBean;

import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class FooCommand extends AbstractConnectedCommand {

    private final ClientWriter cw;

    @Inject FooCommand(ClientWriter cw) {
        this.cw = cw;
    }

    @Override
    public String getName() {
        return "foo";
    }

    @Override
    public void execute(String[] args) throws Exception {
        printMBeanInfo(ObjectName.getInstance("java.lang", "type", "OperatingSystem"), OperatingSystemMXBean.class);
        printMBeanInfo(ObjectName.getInstance("java.lang", "type", "Memory"), MemoryMXBean.class);
    }

    private <T> void printMBeanInfo(ObjectName on, Class<T> klazz) throws Exception {
        T bean = getConnection().getMBean(on, klazz);
        cw.println(klazz.getSimpleName() + ":");
        for (Method method : klazz.getMethods()) {
            if (method.getName().matches("get[\\w]+") && method.getParameterTypes().length == 0) {
                cw.println(String.format("\t%s(): %s", method.getName(), method.invoke(bean).toString()));
            }
        }
    }

    @Override
    public boolean validate(String[] args) {
        return true;
    }

}