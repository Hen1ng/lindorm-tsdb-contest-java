# 工程简介
本工程是比赛时选手需要下载的工程，选手实现工程内 TSDBEngineImpl 类。
评测程序会将选手提交的修改后的本工程代码进行编译并调用选手实现的 TSDBEngineImpl 类的接口进行测试。
其他参赛说明及本文档均未说明或不清楚的问题可询比赛客服。
  

# 注意事项
1. 日志必须使用 std::out 或 std::err，打印到其他地方正式测评时可能无法透传出来。
2. 建议不要频繁打印日志，正式环境中日志的实现可能会产生同步、异步 IO 行为，影响选手成绩。
3. 选手只能修改 TSDBEngineImpl 类，选手可以增加其他类，但不能修改已定义的其他文件。
4. 选手不能使用三方库，评测程序会检查选手是否引入本地三方库，并且正式比赛时编译环境处于断网状态。
5. 选手提交时，将本工程打包到一个 zip 包中，zip 包应将整个 lindorm-tsdb-contest-java 目录打包，而不是将该目录下的内容打包，即最终 zip 包根目录中只有 lindorm-tsdb-contest-java 一个目录：
   + cd .xxxxx/lindorm-tsdb-contest-java
   + cd .. # 退回上级目录
   + add directory to zip package root: ./lindorm-tsdb-contest-java
6. 基础代码选手不可修改的部分（如一些结构体等）我们已经事先进行了 UT 测试，但仍不排除存在 BUG 的可能性，如果选手发现了问题影响参赛，请及时与我们联系。example 目录下的示例不影响参赛，不接受 BUG 报告。
7. 评测时使用 openjdk-13 进行编译，可以使用 java 11 特性，maven.compiler.source == 11。
8. 评测环境为 Linux (Alibaba alios-7) & openjdk-13，选手如果超出 JAVA 直接使用与操作系统有关的调用时应注意此问题。
   

# 工程结构说明
1. example 文件夹：
   + TSDBEngineSample.java: 一个 Sample 数据库实现，继承了 TSDBEngine 类，供选手参考以了解接口语义。
   + EvaluationSample.java：评测程序实例，选手可参考这个类了解评测程序可能会如何执行选手实现的接口。
2. structs 文件夹：定义了一些数据结构。选手不能修改。
3. TSDBEngine.java：接口定义类，选手需要实现对应接口。选手不能修改本类。
4. TSDBEngineImpl.java：选手需要实际实现的类。
  

# 评测程序流程参考
评测程序可能会执行的操作：
   1. 写入测试。
   2. 正确性测试。
   3. 重启，清空缓存。
   4. 重新通过先前的数据目录重启数据库，数据库需要加载之前持久化的数据。
   5. 正确性测试。
   6. 读取性能测试。
   7. 压缩率测试。
  

# 选手自测方法
1. 选手可参考 EvaluationSample.java 中的实现，结合接口语义，自定义测试类测试自己实现的接口。
  

# example 说明
1. example 使用性能较差的方式满足了全部接口的语义，供选手参考。
2. example 为示例代码，可能存在不严谨之处，仅供选手参考，具体接口语义请以参赛说明为准。
  
