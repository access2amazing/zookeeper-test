package priv.wxl.zk.snyc;

import org.apache.zookeeper.*;

import java.io.IOException;

/**
 * @author xueli.wang
 * @since 2020/07/05 18:03
 */

public class SyncPrimitive implements Watcher {
    static ZooKeeper zooKeeper = null;
    static Integer mutex;

    SyncPrimitive(String address) {
        if (zooKeeper == null) {
            try {
                System.out.println("Starting ZooKeeper: ");
                zooKeeper = new ZooKeeper(address, 30000, this);
                mutex = -1;
                System.out.println("Finished Starting ZooKeeper:" + zooKeeper);
            } catch (IOException e) {
                System.out.println(e.toString());
                zooKeeper = null;
            }
        }
    }

    @Override
    public void process(WatchedEvent watchedEvent) {
        synchronized (this) {
            mutex.notify();
        }
    }
}
