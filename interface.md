+ 参见官方赛题说明
  

# TSDBEngine 构造函数
+ 传入 dataPath，创建数据库对象
+ dataPath 为数据库存储数据的本地目录
+ 选手不可以使用 dataPath 以外的目录进行数据存储，否则成绩无效
+ dataPath 目录如果不存在程序会终止，选手测试时需要注意这一点，正式评测时保证目录存在且为空
+ TSDBEngineImpl 选手实现时，构造函数签名不能修改，因为评测程序会使用该构造函数来创建 TSDBEngineImpl 对象，但该构造函数的具体实现可以修改
  

# connect 接口
+ 加载数据库对象，如果 dataPath 内已有数据，则加载，否则创建新数据库
+ 评测程序可能会重启进程，使用相同 dataPath 加载数据库对象，并检查先前写入数据，因此选手必须实现可持久化能力
  

# createTable 接口
+ 创建一个表
+ 参数为表名以及 Schema 信息
+ 初赛中，只会创建一张表
  

# shutdown 接口
+ 关闭已连接的数据库（和 connect 接口组合调用）
+ 当本接口返回时，要求所有数据已落盘持久化
  

# upsert 接口
+ 写入若干行数据到某一表中
+ 传入表名，行（Row）对象，其中每个 Row 对象必须包含全部列，不允许空列（评测程序会保证这一点），注意长度为 0 的字符串不属于空列
+ 接口成功返回后，要求写入的数据立即可读
+ 本接口必须支持并发调用（Multi-thread friendly）
+ 如果数据库中已存在某行（vin + timestamp 组合已存在），覆盖先前的行
+ 选手不可以直接缓存入参对象 wReq 的指针，因为该对象在 upsert 接口返回后会被清理（例如 String ColumnValue ByteBuffer 中的数据）
  

# executeLatestQuery 接口
+ 获取若干 vin 的最新行（该 vin 的所有行中，timestamp 最大的一行）的某些列
+ requestedFields 参数标记了需要获取的列的名称，未在该参数中标记的列不能返回
+ requestedFields 如果为空，代表请求所有列
+ 如果某个 vin 在数据库中不存在，则跳过该 vin
+ 本接口必须支持并发调用（Multi-thread friendly）
+ 返回值必须是 java.util.ArrayList 类型，不能继承该类型返回其子类对象
+ 初赛不考察返回结果 ArrayList 中元素的顺序，只要所有结果都包含在 ArrayList 中即可
  

# executeTimeRangeQuery 接口
+ 获取某一个 vin 若干列 
+ 获取的列的 timestamp 应该位于 timeLowerBound 和 timeUpperBound 之间，不包括 timeUpperBound，包括 timeLowerBound
+ timeLowerBound < timeUpperBound
+ requestedFields 参数标记了需要获取的列的名称，未在该参数中标记的列不能返回
+ 如果 vin 在数据库中不存在，返回空集合
+ 本接口必须支持并发调用（Multi-thread friendly）
+ 返回值必须是 java.util.ArrayList 类型，不能继承该类型返回其子类对象
+ 初赛不考察返回结果 ArrayList 中元素的顺序，只要所有结果都包含在 ArrayList 中即可
  
