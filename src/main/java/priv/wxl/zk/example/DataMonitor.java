package priv.wxl.zk.example;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.KeeperException.Code;

import java.util.Arrays;

/**
 * @author xueli.wang
 * @since 2020/07/03 10:58
 */

public class DataMonitor implements Watcher, AsyncCallback.StatCallback {
    private ZooKeeper zk;
    private String znode;
    private Watcher chainedWatcher;
    boolean dead;
    private DataMonitorListener listener;
    private byte[] prevData;

    public DataMonitor(ZooKeeper zk, String znode, Watcher chainedWatcher, DataMonitorListener listener) {
        this.zk = zk;
        this.znode = znode;
        this.chainedWatcher = chainedWatcher;
        this.listener = listener;
        // Get things started by checking if the node exists.
        // We are going to be completely event driven
        zk.exists(znode, true, this, null);
    }

    /**
     * Other classes use the DataMonitor by implementing this method
     */
    public interface DataMonitorListener {
        /**
         * The existence status of the node has changed
         * @param data data
         */
        void exists(byte[] data);

        /**
         * The ZooKeeper session is no longer valid
         * @param rc the ZooKeeper reason code
         */
         void  closing(int rc);
    }

    @Override
    public void process(WatchedEvent event) {
        String path = event.getPath();
        if (event.getType() == Event.EventType.None) {
            // We are being told that the state of the connection has changed
            switch (event.getState()) {
                case SyncConnected:
                    // In the particular example we don't need to do anything
                    // here - watches war automatically re-registered with
                    // sever and any watches triggered while the client was
                    // disconnected will be delivered (in order of course)
                    break;
                case Expired:
                    // It's all over
                    dead = true;
                    listener.closing(KeeperException.Code.SESSIONEXPIRED.intValue());
                    break;
                default:
                    break;
            }
        } else {
            if (path != null && path.equals(znode)) {
                // Something has changed on the node. let's find out
                zk.exists(znode, true,this, null);
            }
        }

        if (chainedWatcher != null) {
            chainedWatcher.process(event);
        }
    }

    @Override
    public void processResult(int rc, String path, Object ctx, Stat stat) {
        boolean exists;

        switch (rc) {
            case Code.Ok:
                exists = true;
                break;
            case Code.NoNode:
                exists = false;
                break;
            case Code.SessionExpired:
            case Code.NoAuth:
                dead = true;
                listener.closing(rc);
                return;
            default:
                // Retry error
                zk.exists(znode, true, this, null);
                return;
        }

        byte[] bytes = null;
        if (exists) {
            try {
                bytes = zk.getData(znode, false, null);
            } catch (KeeperException e) {
                // We don't need to worry about recovering now.
                // The watch callback will kick off any exception handling
                e.printStackTrace();
            } catch (InterruptedException e) {
                return;
            }
        }

        if ((bytes == null && prevData != null)
                || (bytes != null && !Arrays.equals(prevData, bytes))) {
            listener.exists(bytes);
            prevData = bytes;
        }
    }
}
