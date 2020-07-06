package priv.wxl.zk.snyc;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.Stat;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * @author xueli.wang
 * @since 2020/07/06 14:31
 */

public class ProducerConsumerQueue extends SyncPrimitive{
    private String root;

    private static final String CHILD_NODE_PREFIX = "/element";

    ProducerConsumerQueue(String address, String root) {
        super(address);
        this.root = root;
        if (zooKeeper != null) {
            try {
                Stat stat = zooKeeper.exists(root, false);
                if (stat == null) {
                    zooKeeper.create(root, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE,
                            CreateMode.PERSISTENT);
                }
            } catch (KeeperException e) {
                System.out.println("Keeper exception when instantiating queue: " + e.toString());
            } catch (InterruptedException e) {
                System.out.println("Interrupted exception");
            }
        }
    }

    public boolean produce(int i) throws KeeperException, InterruptedException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        byte[] value;

        byteBuffer.putInt(i);
        value = byteBuffer.array();
        String znodeName = root + CHILD_NODE_PREFIX;
        zooKeeper.create(znodeName, value, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT_SEQUENTIAL);
        return true;
    }

    public int comsume() throws KeeperException, InterruptedException {
        while (true) {
            synchronized (mutex) {
                List<String> children = zooKeeper.getChildren(root, true);
                if (children.size() == 0) {
                    System.out.println("Going to wait");
                    mutex.wait();
                } else {
                    int min = Integer.valueOf(children.get(0).substring(CHILD_NODE_PREFIX.length()));
                    for (String child : children) {
                        int temp = Integer.valueOf(child.substring(CHILD_NODE_PREFIX.length()));
                        if (temp < min) {
                            min = temp;
                        }
                    }
                    String consumeZnodeName = root + CHILD_NODE_PREFIX + min;
                    System.out.println("consume znode: " + consumeZnodeName);
                    byte[] bytes = zooKeeper.getData(consumeZnodeName, false, null);
                    zooKeeper.delete(consumeZnodeName, 0);

                    return ByteBuffer.wrap(bytes).getInt();
                }
            }
        }
    }
}
