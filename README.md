# ZooKeeper: Because Coordinating Distributed Systems is a Zoo

## Overview

## Developer

### ZooKeeper Recipes and Solutions

#### A Guide to Creating Higher-level Constructs with ZooKeeper

尽管ZooKeeper使用异步的通知，但可以用来构建同步的一致性原语，比如队列和锁。这是因为ZooKeeper的更新基于整体顺序的（an overall order），并且对外暴露这种顺序。

这里没有纳入许多其他的优雅功能，比如可撤销的读写优先级锁（revocable read-write priority locks）。

##### Important Note About Error Handling

当实现这些recipes的时候必须处理可恢复的异常。值得注意的是，许多recipes会采用序列临时节点。在创建序列临时节点时，存在一种错误情形：server成功执行了*create()*方法，但是向客户端返回节点名称之前server崩溃了。当客户端重新连接时，会话依然是有效的，因此节点不会被移除。对客户端来说，判断节点是否已经创建是很困难的。

##### Out of the Box Applications: Name Service, Configuration, Group Membership

命名服务和配置是ZooKeeper的两个主要应用，其功能由ZooKeeper API直接提供。

由ZooKeeper直接提供的另一个功能是*group membership*。group由节点表示，group中的members在group节点下面创建临时节点。非正常失败的member节点在被ZooKeeper检测到后会被自动移除。

##### Barriers

分布式系统使用*barriers*阻塞一组节点对应的执行过程，直到满足一定条件后全部节点对应的执行过程都允许继续执行。在ZooKeeper中，通过指派一个barrier节点实现Barriers，barrier节点存在说明存在阻塞。伪代码如下：

1. 客户端在barrier节点上调用ZooKeeper API **exists()**，并将*watch*设为true
2. 如果**exists()**返回false，则说明不存在阻塞，客户端继续执行
3. 如果**exists()**返回true，则客户端在barrier节点的watch事件上等待
4. 当watch事件被触发时，客户端再次调用**exists()**，等待直到barrier节点被移除

###### Double Barriers

double barries允许客户端同步计算的开始与结束。当足够多的执行过程加入到barrier时，执行过程开始执行计算，一旦执行完毕就离开barrier。

在本例的伪代码中用*b*表示barrier节点。每个客户端执行过程*p*进入时注册到barrier节点上，离开时从barrier节点中删除。一个节点通过**Enter**注册到barrier节点上，在执行计算之前会等待到*x*个节点注册完毕。

| **Enter**                                                    | **Leave**                                                    |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| 1. 设置节点名称 *n = b + “/” + p* <br/>2. 设置watch：**existes(b + "/ready", true)** <br />3. 创建节点：**create(*n*, EPHEMERAL)**<br />4. **L = getChildren(b, false)**<br />5. 如果L小于x，等待watch事件<br />6. 否则**create(b + "ready", REGULAR)** | 1. **L = getChildren(b, false)**<br />2. 如果不存在执行过程的节点，直接退出<br />3. 如果*p*是唯一的执行过程节点，删除节点后退出<br />4. 如果*p*是L中的最低执行过程节点，则在最高执行过程节点上等待<br />5. 否则 ***delete(n)***，在L中的最低执行过程节点上等待<br />6. goto 1 |

On entering, all processes watch on a ready node and create an ephemeral node as a child of the barrier node. Each process but the last enters the barrier and waits for the ready node to appear at line 5. The process that creates the xth node, the last process, will see x nodes in the list of children and create the ready node, waking up the other processes. Note that waiting processes wake up only when it is time to exit, so waiting is efficient.

On exit, you can't use a flag such as *ready* because you are watching for process nodes to go away. By using ephemeral nodes, processes that fail after the barrier has been entered do not prevent correct processes from finishing. When processes are ready to leave, they need to delete their process nodes and wait for all other processes to do the same.

Processes exit when there are no process nodes left as children of *b*. However, as an efficiency, you can use the lowest process node as the ready flag. All other processes that are ready to exit watch for the lowest existing process node to go away, and the owner of the lowest process watches for any other process node (picking the highest for simplicity) to go away. This means that only a single process wakes up on each node deletion except for the last node, which wakes up everyone when it is removed.

##### Queues

分布式队列是常见的数据结构。在ZooKeeper中实现分布式队列时，首先需要指定一个持有队列的zonde，称为队列节点。客户端通过调用*create()*加入队列，其中路径名称以“queue-”开头，节点类型为*sequence*和*ephemeral*。由于设置了*sequence*，新的路径名称的格式为 *path-to-queue-node/queue-X*，其中X是单调递增的。客户端希望移除队列中的节点时，调用**getChildren()**，将*watch*设置为true，开始从最低编号处理节点。在用完第一次调用**getChildren()**获取的节点列表之前客户端不需要再调用**getChildren()**。如果队列中没有孩子节点，消费者需要等待watch通知后再次检查队列。

###### Priority Queues

对前面的队列进行两处小的改变就可以实现优先级队列。在加入到队列时，路径名称“queue-YY”中的“YY”表示优先级，数值越低，优先级越高。从队列中移除时，客户端使用即时更新的孩子节点列表，这意味着当watch触发了，客户端之前获取的孩子节点列表将是无效的。

##### Locks

完全的分布式锁是全局同步的，这意味着在任何时间的快照中，没有任何两个客户端持有相同的锁。

客户端获取锁的流程如下：

1. 调用**create()**，其中路径为"*locknode/guide-lock-*"，并设置*sequence*和*ephemeral*。以防出现create()结果丢失的情况，*guid*是需要的。
2. 在lock节点上调用 **getChildren()**，不需要设置watch（这样才能避免羊群效应）
3. 如果在步骤**1**中创建的路径的编号是最小的，则该客户端得到锁，并退出
4. 否则客户端在第一个编号比它小的节点上调用**exists()**，并设置watch
5. 如果**exists()**返回null，回到步骤**2**。否则在回到步骤**2**之前步骤**4**中节点watch上等待。

释放锁非常简单，客户端只需要删除在回到步骤**1**中创建的节点。

值得注意的是：

- 由于一个节点只会被一个客户端watch，移除一个节点只会唤醒一个客户端。这种方式可以避免羊群效应
- 没有轮询和超时
- 采用这种实现锁的方式很容易看到竞争锁的数量、中断锁，调试锁问题等

可恢复的错误和GUID

- 如果调用**create()**时出现了可恢复的错误，客户端应该调用**getChildren()**，检查路径中是否已经存在包含*guid*的节点。

###### Shared Locks

实现读写锁需要进行一些调整：

| 获取读锁                                                     | 获取写锁                                                     |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| 1. 调用**create()**，其中路径为"*guid-/read-*"，并设置*sequence*和*ephemeral*<br />2. 在lock节点上调用 **getChildren()**，不需要设置watch<br />3. 如果不存在以"*write-*"开头的孩子节点，且相应节点的序号比步骤**1**中创建的路径低，则客户端得到锁，并退出<br />4. 否则客户端在第一个编号以"*write-*"开头的比它小的节点上调用**exists()**，并设置watch<br />5. 如果**exists()**返回null，回到步骤**2**。否则在回到步骤**2**之前步骤**4**中节点watch上等待。 | 1. 调用**create()**，其中路径为"*guid-/write-*"，并设置*sequence*和*ephemeral*<br />2. 在lock节点上调用 **getChildren()**，不需要设置watch<br />3. 如果在步骤**1**中创建的路径的编号是最小的，则该客户端得到锁，并退出<br />4. 否则客户端在第一个编号比它小的节点上调用**exists()**，并设置watch<br />5. 如果**exists()**返回null，回到步骤**2**。否则在回到步骤**2**之前步骤**4**中节点watch上等待。 |

值得注意的是：读写锁可能会出现羊群效应，当写锁被释放时，多个等待在写锁上的客户端会同时得到锁

###### Revocable Shared Locks

对读写锁进行一定的改造，可以实现可撤销的读写锁

In step **1**, of both obtain reader and writer lock protocols, call **getData( )** with *watch* set, immediately after the call to **create( )**. If the client subsequently receives notification for the node it created in step **1**, it does another **getData( )** on that node, with *watch* set and looks for the string "unlock", which signals to the client that it must release the lock. This is because, according to this shared lock protocol, you can request the client with the lock give up the lock by calling **setData()** on the lock node, writing "unlock" to that node.

Note that this protocol requires the lock holder to consent to releasing the lock. Such consent is important, especially if the lock holder needs to do some processing before releasing the lock. Of course you can always implement *Revocable Shared Locks with Freaking Laser Beams* by stipulating in your protocol that the revoker is allowed to delete the lock node if after some length of time the lock isn't deleted by the lock holder.

##### Two-phased Commit

A two-phase commit protocol is an algorithm that lets all clients in a distributed system agree either to commit a transaction or abort.

In ZooKeeper, you can implement a two-phased commit by having a coordinator create a transaction node, say "/app/Tx", and one child node per participating site, say "/app/Tx/s_i". When coordinator creates the child node, it leaves the content undefined. Once each site involved in the transaction receives the transaction from the coordinator, the site reads each child node and sets a watch. Each site then processes the query and votes "commit" or "abort" by writing to its respective node. Once the write completes, the other sites are notified, and as soon as all sites have all votes, they can decide either "abort" or "commit". Note that a node can decide "abort" earlier if some site votes for "abort".

An interesting aspect of this implementation is that the only role of the coordinator is to decide upon the group of sites, to create the ZooKeeper nodes, and to propagate the transaction to the corresponding sites. In fact, even propagating the transaction can be done through ZooKeeper by writing it in the transaction node.

There are two important drawbacks of the approach described above. One is the message complexity, which is O(n²). The second is the impossibility of detecting failures of sites through ephemeral nodes. To detect the failure of a site using ephemeral nodes, it is necessary that the site create the node.

To solve the first problem, you can have only the coordinator notified of changes to the transaction nodes, and then notify the sites once coordinator reaches a decision. Note that this approach is scalable, but it's is slower too, as it requires all communication to go through the coordinator.

To address the second problem, you can have the coordinator propagate the transaction to the sites, and have each site creating its own ephemeral node.

##### Leader Election

A simple way of doing leader election with ZooKeeper is to use the **SEQUENCE|EPHEMERAL** flags when creating znodes that represent "proposals" of clients. The idea is to have a znode, say "/election", such that each znode creates a child znode "/election/guid-n_" with both flags SEQUENCE|EPHEMERAL. With the sequence flag, ZooKeeper automatically appends a sequence number that is greater than any one previously appended to a child of "/election". The process that created the znode with the smallest appended sequence number is the leader.

That's not all, though. It is important to watch for failures of the leader, so that a new client arises as the new leader in the case the current leader fails. A trivial solution is to have all application processes watching upon the current smallest znode, and checking if they are the new leader when the smallest znode goes away (note that the smallest znode will go away if the leader fails because the node is ephemeral). But this causes a herd effect: upon a failure of the current leader, all other processes receive a notification, and execute getChildren on "/election" to obtain the current list of children of "/election". If the number of clients is large, it causes a spike on the number of operations that ZooKeeper servers have to process. To avoid the herd effect, it is sufficient to watch for the next znode down on the sequence of znodes. If a client receives a notification that the znode it is watching is gone, then it becomes the new leader in the case that there is no smaller znode. Note that this avoids the herd effect by not having all clients watching the same znode.

Here's the pseudo code:

Let ELECTION be a path of choice of the application. To volunteer to be a leader:

1. Create znode z with path "ELECTION/guid-n_" with both SEQUENCE and EPHEMERAL flags;
2. Let C be the children of "ELECTION", and i be the sequence number of z;
3. Watch for changes on "ELECTION/guid-n_j", where j is the largest sequence number such that j < i and n_j is a znode in C;

Upon receiving a notification of znode deletion:

1. Let C be the new set of children of ELECTION;
2. If z is the smallest node in C, then execute leader procedure;
3. Otherwise, watch for changes on "ELECTION/guid-n_j", where j is the largest sequence number such that j < i and n_j is a znode in C;

Notes:

- Note that the znode having no preceding znode on the list of children do not imply that the creator of this znode is aware that it is the current leader. Applications may consider creating a separate znode to acknowledge that the leader has executed the leader procedure.
- See the [note for Locks](https://zookeeper.apache.org/doc/current/recipes.html#sc_recipes_GuidNote) on how to use the guid in the node.