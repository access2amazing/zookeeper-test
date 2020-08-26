import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class NewZooKeeperTest {
    private static String CONNECT_STRING ="localhost:2181,localhost:2182,localhost:2183";
    private static int SESSION_TIMEOUT = 2000;
    private ZooKeeper zkClient;

    @Before
    public void init() throws IOException {
        zkClient = new ZooKeeper(CONNECT_STRING, SESSION_TIMEOUT, watchedEvent -> {
            System.out.println("hello world");
            System.out.println(watchedEvent.getState());
            try {
                List<String> children = zkClient.getChildren("/", true);
                System.out.println(children);
            } catch (Exception e) {
                System.out.println(e.toString());
            }
        });
    }

    @Test
    public void createNode() throws KeeperException, InterruptedException {
        String path = zkClient
                .create("/liangshan", "songjiang".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        System.out.println(path);
    }

    @Test
    public void getDataAndWatch() throws KeeperException, InterruptedException {
        List<String> children = zkClient.getChildren("/", true);
        System.out.println(children);

        // 延时阻塞
        Thread.sleep(100000);
    }

    @Test
    public void exist() throws KeeperException, InterruptedException {
        Stat stat = zkClient.exists("/liangshan", false);
        System.out.println(stat == null ? "not exist" : "exist");
    }
}
