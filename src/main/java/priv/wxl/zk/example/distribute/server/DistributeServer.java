package priv.wxl.zk.example.distribute.server;

import org.apache.zookeeper.*;

import java.io.IOException;

public class DistributeServer {
    public static void main(String[] args) throws Exception {
        DistributeServer server = new DistributeServer();
        // 1 connect to zk service
        server.getConnect();

        // 2 registry nodes
        server.registry(args[0]);

        // 3 process business logic
        server.business();
    }

    private void registry(String hostName) throws KeeperException, InterruptedException {
        String path = zkClient.create("/servers/server", hostName.getBytes(),
                ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);

        System.out.println(hostName + " is online");
    }

    private void business() throws InterruptedException {
        Thread.sleep(Integer.MAX_VALUE);
    }

    private String connectString = "localhost:2181,localhost:2182,localhost:2183";

    private int sessionTimeout = 2000;

    private ZooKeeper zkClient;

    private void getConnect() throws IOException {
        zkClient = new ZooKeeper(connectString, sessionTimeout, event -> {

        });
    }
}
