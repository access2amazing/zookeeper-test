package priv.wxl.zk.snyc;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * @author xueli.wang
 * @since 2020/07/05 18:03
 */

public class SyncPrimitive implements Watcher {
    static ZooKeeper zooKeeper = null;
    static Integer mutex;
    String root;

    SyncPrimitive(String address) {
        if (zooKeeper == null) {
            try {
                System.out.println("Starting ZooKeeper: ");
                zooKeeper = new ZooKeeper(address, 3000, this);
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

    static public class Barrier extends SyncPrimitive {
        int size;
        String name;

        Barrier(String address, String root, int size) {
            super(address);
            this.root = root;
            this.size = size;

            // create barrier node
            if (zooKeeper != null) {
                try {
                    Stat stat = zooKeeper.exists(root, false);
                    if (stat == null) {
                        zooKeeper.create(root, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                    }
                } catch (KeeperException e) {
                    System.out.println("KeeperException when instantiating queue: " + e.toString());
                } catch (InterruptedException e) {
                    System.out.println("Interrupted Exception");
                }
            }

            // my node name
            try {
                name = InetAddress.getLocalHost().getCanonicalHostName().toString();
            } catch (UnknownHostException e) {
                System.out.println(e.toString());
            }
        }

        boolean enter() throws KeeperException, InterruptedException {
            zooKeeper.create(root + "/" + name, new byte[0],
                    ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            while (true) {
                synchronized (mutex) {
                    List<String> children = zooKeeper.getChildren(root, true);
                    if (children.size() < size) {
                        mutex.wait();
                    } else {
                        return true;
                    }
                }
            }
        }

        boolean leave() throws KeeperException, InterruptedException {
            zooKeeper.delete(root + "/" + name, 0);
            while (true) {
                synchronized (mutex) {
                    List<String> children = zooKeeper.getChildren(root, true);
                    if (children.size() > 0) {
                        mutex.wait();
                    } else {
                        return true;
                    }
                }
            }
        }
    }

    static public class Queue extends SyncPrimitive {
        Queue(String address, String name) {
            super(address);
            this.root = name;
            if (zooKeeper != null) {
                try {
                    Stat stat = zooKeeper.exists(root, false);
                    if (stat == null) {
                        zooKeeper.create(root, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                    }
                } catch (KeeperException e) {
                    System.out.println("KeeperException when instantiating queue: " + e.toString());
                } catch (InterruptedException e) {
                    System.out.println("Interrupted Exception");
                }
            }
        }
    }
}
