import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author xueli.wang
 * @since 2020/07/02 08:57
 */

public class Connect2ZooKeeperTest {
    /**
     * 会话超时时间，与系统默认时间一致
     */
    private static final int SESSION_TIMEOUT = 30 * 1000;

    private ZooKeeper zooKeeper;

    private static final String ZK_NODE_FOR_TEST = "/test";

    private Watcher watcher = (watchedEvent) -> System.out.println("watchedEvent >>> " + watchedEvent.toString());

    private void createZooKeeperInstance() throws IOException {
        zooKeeper = new ZooKeeper("localhost:2181,localhost:2182,localhost:2183", SESSION_TIMEOUT, watcher);
    }

    private void operateZooKeeper() throws InterruptedException, KeeperException {
        zooKeeper.create(ZK_NODE_FOR_TEST, "1".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

        System.out.println(new String(zooKeeper.getData(ZK_NODE_FOR_TEST, watcher, null)));

        Stat stat = zooKeeper.setData(ZK_NODE_FOR_TEST, "2".getBytes(), -1);

        System.out.println(new String(zooKeeper.getData(ZK_NODE_FOR_TEST, watcher, null)));

        stat = zooKeeper.setData(ZK_NODE_FOR_TEST, "3".getBytes(), stat.getVersion());

        System.out.println(new String(zooKeeper.getData(ZK_NODE_FOR_TEST, watcher, null)));

        zooKeeper.setData(ZK_NODE_FOR_TEST, "4".getBytes(), stat.getVersion());

        System.out.println(new String(zooKeeper.getData(ZK_NODE_FOR_TEST, watcher, null)));

        zooKeeper.delete(ZK_NODE_FOR_TEST, -1);

        System.out.println("zk node status: [" + zooKeeper.exists(ZK_NODE_FOR_TEST, false) + "]");
    }

    private void closeZooKeeper() throws InterruptedException {
        zooKeeper.close();
    }

    @Test
    public void testConnectZooKeeper() throws IOException, InterruptedException, KeeperException {
        createZooKeeperInstance();
        operateZooKeeper();
        closeZooKeeper();
    }

    @Test
    public void testEmptyListStream() {
        List<String> empty = new ArrayList<>();
        String temp = "temp";
        empty.add(temp);
        System.out.println(empty
                .stream()
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toList())
                .stream()
                .anyMatch((ele) -> ele.equals(temp)));
    }

    @Test
    public void testStreamCollect() {
        List<Double> doubles = new ArrayList<>();
        doubles.add(2.7D);
        System.out.println(doubles.stream()
                // 推荐车长(double)乘以10转换为整数(int)得到车长编码
                .map((length) -> length * 10)
                .map(Double::intValue)
                // 根据车长编码字典过滤推荐车长
                .filter(this::filterTruckLengthByDictionary)
                .map(Objects::toString)
                // 组装为英文逗号隔开的String
                .collect(Collectors.joining(",")));
    }

    private boolean filterTruckLengthByDictionary(Integer truckLength) {
        List<Integer> integers = new ArrayList<>();
        integers.add(18);
        integers.add(27);
        return integers.stream()
                .anyMatch(dictionaryTruckLength -> dictionaryTruckLength.equals(truckLength));
    }
}
