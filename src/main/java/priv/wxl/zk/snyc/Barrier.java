package priv.wxl.zk.snyc;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.Stat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author xueli.wang
 * @since 2020/07/06 13:54
 */

public class Barrier extends SyncPrimitive {
    private String root;

    private int size;

    private String name;

    private List<String> childPaths = new ArrayList<>();

    public Barrier(String address, String root, int size) {
        super(address);
        this.size = size;
        this.root = root;
        // create barrier node
        if (zooKeeper != null) {
            try {
                Stat stat = zooKeeper.exists(root, false);
                if (stat == null) {
                    zooKeeper.create(root, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE,
                            CreateMode.PERSISTENT);
                }
            } catch (KeeperException e) {
                System.out.println("Keeper exception when instantiating barrier: " + e.toString());
            } catch (InterruptedException e) {
                System.out.println("Interrupted exception");
            }
        }

        try {
            name = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            System.out.println(e.toString());
        }
    }

    /**
     * Join barrier
     *
     * @return whether joining barrier is successful
     * @throws KeeperException zk exception
     * @throws InterruptedException interrupted
     */
    public boolean enter() throws KeeperException, InterruptedException {
        String znodeName = root + "/" + name;
        String childPath = zooKeeper.create(znodeName, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL);
        System.out.println(childPath);
        childPaths.add(childPath);
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

    public boolean leave() throws KeeperException, InterruptedException {
        if (childPaths.isEmpty()) {
            return false;
        }
        String childPath = childPaths.get(0);
        zooKeeper.delete(childPath, -1);
        System.out.println(childPath);
        childPaths.remove(0);
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
