# kafkaLastHomework
kafkaLastHomework

It's the last home work for Kafka class.
------------------------------------------
将代码及创建相关Topic的脚本上传至Github
代码须包含读文件中的数据并发布到Kafka的Producer；自定义的Partitioner；Kafka Stream应用的代码（DSL）
------------------------------------------

读文件中的数据并发布到Kafka的Producer
------------------------------------------
https://github.com/joeshun123/kafkaLastHomework/tree/master/demokafka.0.10.1.0/src/main/java/com/jasongj/kafka/stream/producer

自定义的Partitioner
------------------------------------------
https://github.com/joeshun123/kafkaLastHomework/blob/master/demokafka.0.10.1.0/src/main/java/com/jasongj/kafka/producer/HashPartitioner.java

Kafka Stream应用的代码（DSL）
------------------------------------------
https://github.com/joeshun123/kafkaLastHomework/blob/master/demokafka.0.10.1.0/src/main/java/com/jasongj/kafka/stream/PurchaseAnalysis3.java

创建相关Topic的脚本:
------------------------------------------
docker-compose up --build
docker exec -it kafka0 bash
cd /opt/kafka/kafka_2.11-0.10.1.0
source /root/.bash_profile

bin/kafka-topics.sh --create --topic orders --zookeeper zookeeper0:2181 --partitions 3 --replication-factor 1
bin/kafka-topics.sh --create --topic users --zookeeper zookeeper0:2181 --partitions 3 --replication-factor 1
bin/kafka-topics.sh --create --topic items --zookeeper zookeeper0:2181 --partitions 6 --replication-factor 1
bin/kafka-topics.sh --create --topic orderuser-repartition-by-item --zookeeper zookeeper0:2181 --partitions 6 --replication-factor 1
bin/kafka-topics.sh --create --topic reportitem-repartition-by-item --zookeeper zookeeper0:2181 --partitions 6 --replication-factor 1
bin/kafka-topics.sh --create --topic reportItem-repartition-by-category --zookeeper zookeeper0:2181 --partitions 6 --replication-factor 1
bin/kafka-topics.sh --create --topic output --zookeeper zookeeper0:2181 --partitions 3 --replication-factor 1

bin/kafka-topics.sh --describe --zookeeper zookeeper0:2181
bin/kafka-console-consumer.sh --bootstrap-server kafka0:9092 --topic output --from-beginning
