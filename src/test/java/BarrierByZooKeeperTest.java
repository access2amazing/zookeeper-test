import org.junit.Test;
import priv.wxl.zk.snyc.Barrier;

import java.util.Random;
import java.util.concurrent.CyclicBarrier;

/**
 * @author xueli.wang
 * @since 2020/07/06 14:52
 */

public class BarrierByZooKeeperTest {
    @Test
    public void barrierTest() {
        String address = "127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183";
        String root = "/b1";

        Barrier barrier = new Barrier(address, root, 1);
        enter(barrier);
        enter(barrier);

        Random random = new Random();
        int r = random.nextInt(10);
        for (int i = 0; i < r; i++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        CyclicBarrier cyclicBarrier = new CyclicBarrier(2);

        new Thread(() -> {
            try {
                cyclicBarrier.await();
            } catch (Exception e) {
                e.printStackTrace();
            }
            leave(barrier);
        }).start();

        new Thread(() -> {
            try {
                cyclicBarrier.await();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            leave(barrier);
        }).start();
    }

    private void enter(Barrier barrier) {
        try {
            boolean flag = barrier.enter();
            System.out.println("Entered barrier");
            if (!flag) {
                System.out.println("Error when entering the barrier");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void leave(Barrier barrier) {
        try {
            barrier.leave();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Left barrier");
    }
}
