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

由ZooKeeper直接提供的另一个功能是*group membership*。group由节点表示，group中的members在group节点下面创建临时节点

