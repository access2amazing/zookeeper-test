package priv.wxl.zk.example;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A simple example program to use DataMonitor to start and
 * stop executables based on a znode. The program watches the
 * specified znode and saves the data that corresponds to the
 * znode in the filesystem. It also starts the specified program
 * with the arguments when znode exists and kills
 * the program if the znode goes away.
 * @author xueli.wang
 * @since 2020/07/02 17:38
 */

public class Executor implements Runnable, Watcher, DataMonitor.DataMonitorListener {
    private String filename;

    private String[] exec;

    private Process child;

    private DataMonitor dataMonitor;

    private Executor(String hostPort, String  znode, String filename, String[] exec)
            throws IOException {
        this.filename = filename;
        this.exec = exec;
        ZooKeeper zk = new ZooKeeper(hostPort, 3000, this);
        dataMonitor = new DataMonitor(zk, znode, null, this);
    }

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("USAGE: Executor hostPort znode filename program [args...]");
            System.exit(2);
        }

        String hostPort = args[0];
        String znode = args[1];
        String filename = args[2];
        String[] exec = new String[args.length - 3];
        System.arraycopy(args, 3, exec, 0, exec.length);

        try {
            new Executor(hostPort, znode, filename, exec).run();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * We do process any events ourselves, we just need to forward them on
     *
     * @see org.apache.zookeeper.Watcher#process(WatchedEvent)
     * @param event WatchedEvent
     */
    @Override
    public void process(WatchedEvent event) {
        dataMonitor.process(event);
    }

    @Override
    public void run() {
        try {
            synchronized (this) {
                while (!dataMonitor.dead) {
                    wait();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void closing(int rc) {
        synchronized (this) {
            notifyAll();
        }
    }

    static class StreamWriter extends Thread {
        OutputStream outputStream;

        InputStream inputStream;

        StreamWriter(OutputStream outputStream, InputStream inputStream) {
            this.outputStream = outputStream;
            this.inputStream = inputStream;
            start();
        }

        @Override
        public void run() {
            byte[] bytes = new byte[80];
            int rc;
            try {
                while ((rc = inputStream.read(bytes)) > 0) {
                    outputStream.write(bytes, 0, rc);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void exists(byte[] data) {
        if (data == null) {
            if (child != null) {
                System.out.println("Killing process");
                child.destroy();
                try {
                    child.waitFor();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } else {
            if (child != null) {
                System.out.println("Stopping child");
                child.destroy();
                try {
                    child.waitFor();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(filename);
                fileOutputStream.write(data);
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                System.out.println("Starting child");
                child = Runtime.getRuntime().exec(exec);
                new StreamWriter(System.out, child.getInputStream());
                new StreamWriter(System.err, child.getErrorStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
