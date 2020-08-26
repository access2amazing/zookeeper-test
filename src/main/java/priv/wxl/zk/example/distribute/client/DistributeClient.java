package priv.wxl.zk.example.distribute.client;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class DistributeClient {
    public static void main(String[] args) throws Exception {
        DistributeClient client = new DistributeClient();

        // 1 connect ZooKeeper
        client.getConnect();

        // 2 registry and watch
        client.getChildren();

        // 3 business logic
        client.business();
    }

    private void getChildren() throws KeeperException, InterruptedException {
        List<String> children = zkClient.getChildren("/servers", true);

        List<String> hosts = children.stream()
                .map(child -> {
                    byte[] data = new byte[1];
                    try {
                        data = zkClient.getData("/servers/" + child, false, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return data;
                })
                .map(String::new)
                .collect(Collectors.toList());

        System.out.println(hosts);
    }

    private void business() throws InterruptedException {
        Thread.sleep(Integer.MAX_VALUE);
    }

    private String connectString = "localhost:2181,localhost:2182,localhost:2183";

    private int sessionTimeout = 2000;

    private ZooKeeper zkClient;

    private void getConnect() throws IOException {
        zkClient = new ZooKeeper(connectString, sessionTimeout, event -> {
            try {
                getChildren();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
