package com.jasongj.kafka.stream;

import com.jasongj.kafka.stream.model.*;
import com.jasongj.kafka.stream.serdes.SerdesFactory;
import com.jasongj.kafka.stream.timeextractor.OrderTimestampExtractor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;

import java.io.IOException;
import java.util.Properties;

public class PurchaseAnalysis3 {

	public static void main(String[] args) throws IOException, InterruptedException {
		Properties props = new Properties();
		props.put(StreamsConfig.APPLICATION_ID_CONFIG, "streams-purchase-analysis2");
		props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka0:9092");
		props.put(StreamsConfig.ZOOKEEPER_CONNECT_CONFIG, "zookeeper0:2181");
		props.put(StreamsConfig.KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
		props.put(StreamsConfig.VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(StreamsConfig.TIMESTAMP_EXTRACTOR_CLASS_CONFIG, OrderTimestampExtractor.class);

		KStreamBuilder streamBuilder = new KStreamBuilder();
		KStream<String, Order> orderStream = streamBuilder.stream(Serdes.String(), SerdesFactory.serdFrom(Order.class), "orders");
		KTable<String, User> userTable = streamBuilder.table(Serdes.String(), SerdesFactory.serdFrom(User.class), "users", "users-state-store");
		KTable<String, Item> itemTable = streamBuilder.table(Serdes.String(), SerdesFactory.serdFrom(Item.class), "items", "items-state-store");

		KStream<String, String> jointedBaseTable = orderStream
				.leftJoin(userTable, (Order order, User user) -> OrderUser.fromOrderUser(order, user), Serdes.String(), SerdesFactory.serdFrom(Order.class))
				/*.foreach((name, orderUser)->System.out.println("after1Join:=>" + name + " " + orderUser.toString()));*/
				.filter((String userName, OrderUser orderUser) -> orderUser.getAge() >= 18 && orderUser.getAge() <= 35)
				.map((String userName, OrderUser orderUser) -> new KeyValue<>(orderUser.itemName, orderUser))
				.through(Serdes.String(), SerdesFactory.serdFrom(OrderUser.class), (String key, OrderUser orderUser, int numPartitions)
						-> (orderUser.getItemName().hashCode() & 0x7FFFFFFF) % numPartitions, "orderuser-repartition-by-item")
				.leftJoin(itemTable, (OrderUser orderUser, Item item) -> OrderUserItem.fromOrderUserItem(orderUser, item), Serdes.String(), SerdesFactory.serdFrom(OrderUser.class))
				/*.foreach((name, orderUserItem) -> System.out.println("after2Join:=>" + name + " " + orderUserItem.toString()));*/
				.filter((itemName, orderItem) -> orderItem.getItemAddress() != null)
				.map((k, v) -> KeyValue.pair(k, ReportItem.fromOrderUserItem(v)))
				.through(Serdes.String(), SerdesFactory.serdFrom(ReportItem.class), (String key, ReportItem orderUser, int numPartitions)
						-> (orderUser.getItemName().hashCode() & 0x7FFFFFFF) % numPartitions, "reportitem-repartition-by-item")
				/*.foreach((name, reportItem) -> System.out.println("after2ReportItem:=>" + name + " " + reportItem.toString()));*/
				.groupByKey()
				.aggregate(ReportItem::new, (aggKey, value, aggregate) -> {
							aggregate.setItemName(value.getItemName());
							aggregate.setType(value.getType());
							aggregate.setTransactionDate(value.getTransactionDate());
							aggregate.setQuantity(value.getQuantity());
							aggregate.setPrice(value.getPrice());
							aggregate.setAmount(aggregate.getAmount() + aggregate.getQuantity() * aggregate.getPrice());
							return aggregate;
						},
						TimeWindows.of(3600000).advanceBy(5000),
						SerdesFactory.serdFrom(ReportItem.class),
						"amount-store1")
				/*.foreach((name, reportItem) -> System.out.println("after1Agg:=>" + name + " " + reportItem.toString());*/
				.toStream()
				.map((Windowed<String> window, ReportItem value) -> new KeyValue<>(value.getType(), value))
				.through(Serdes.String(), SerdesFactory.serdFrom(ReportItem.class), (String key, ReportItem orderUser, int numPartitions)
						-> (orderUser.getItemName().hashCode() & 0x7FFFFFFF) % numPartitions, "reportItem-repartition-by-category")
				/*.foreach((name, reportItem) -> System.out.println("after2KeyCat:=>" + name + " " + reportItem.toString()));*/
				.groupByKey()
				.aggregate(ReportItem::new, (aggKey, value, aggregate) -> {
							aggregate.setItemName(value.getItemName());
							aggregate.setType(value.getType());
							aggregate.setTransactionDate(value.getTransactionDate());
							aggregate.setQuantity(value.getQuantity());
							aggregate.setPrice(value.getPrice());
							aggregate.setAmount(value.getAmount());
							aggregate.calculateRank();
							return aggregate;
						},
						TimeWindows.of(3600000).advanceBy(5000),
						SerdesFactory.serdFrom(ReportItem.class),
						"rank-store1")
				/*.foreach((name, reportItem) -> System.out.println("after2Agg:=>" + name + " " + reportItem.toString()));*/
				.toStream()
				.map((Windowed<String> window, ReportItem value) -> {
					return new KeyValue<>(window.key(), String.format("key=%s, start=%d, end=%d, value=%s",
							window.key(), window.window().start(), window.window().end(), value.toString()));
				});
		jointedBaseTable.to(Serdes.String(), Serdes.String(), "output");

		KafkaStreams streams = new KafkaStreams(streamBuilder, props);
		streams.start();
		Thread.sleep(100000L);
		streams.close();
	}

	public static class OrderUser {
		private String userName;
		private String itemName;
		private long transactionDate;
		private int quantity;
		private String userAddress;
		private String gender;
		private int age;

		public String getUserName() {
			return userName;
		}

		public void setUserName(String userName) {
			this.userName = userName;
		}

		public String getItemName() {
			return itemName;
		}

		public void setItemName(String itemName) {
			this.itemName = itemName;
		}

		public long getTransactionDate() {
			return transactionDate;
		}

		public void setTransactionDate(long transactionDate) {
			this.transactionDate = transactionDate;
		}

		public int getQuantity() {
			return quantity;
		}

		public void setQuantity(int quantity) {
			this.quantity = quantity;
		}

		public String getUserAddress() {
			return userAddress;
		}

		public void setUserAddress(String userAddress) {
			this.userAddress = userAddress;
		}

		public String getGender() {
			return gender;
		}

		public void setGender(String gender) {
			this.gender = gender;
		}

		public int getAge() {
			return age;
		}

		public void setAge(int age) {
			this.age = age;
		}

		public static OrderUser fromOrder(Order order) {
			OrderUser orderUser = new OrderUser();
			if(order == null) {
				return orderUser;
			}
			orderUser.userName = order.getUserName();
			orderUser.itemName = order.getItemName();
			orderUser.transactionDate = order.getTransactionDate();
			orderUser.quantity = order.getQuantity();
			return orderUser;
		}

		public static OrderUser fromOrderUser(Order order, User user) {
			OrderUser orderUser = fromOrder(order);
			if(user == null) {
				return orderUser;
			}
			orderUser.gender = user.getGender();
			orderUser.age = user.getAge();
			orderUser.userAddress = user.getAddress();
			return orderUser;
		}

		@Override
		public String toString() {
			return "OrderUser{" +
					"userName='" + userName + '\'' +
					", itemName='" + itemName + '\'' +
					", transactionDate=" + transactionDate +
					", quantity=" + quantity +
					", userAddress='" + userAddress + '\'' +
					", gender='" + gender + '\'' +
					", age=" + age +
					'}';
		}
	}
	public static class OrderUserItem {
		private String userName;
		private String itemName;
		private long transactionDate;
		private int quantity;
		private String itemAddress;
		private String itemType;
		private double itemPrice;

		public String getUserName() {
			return userName;
		}

		public void setUserName(String userName) {
			this.userName = userName;
		}

		public String getItemName() {
			return itemName;
		}

		public void setItemName(String itemName) {
			this.itemName = itemName;
		}

		public long getTransactionDate() {
			return transactionDate;
		}

		public void setTransactionDate(long transactionDate) {
			this.transactionDate = transactionDate;
		}

		public int getQuantity() {
			return quantity;
		}

		public void setQuantity(int quantity) {
			this.quantity = quantity;
		}

		public String getItemAddress() {
			return itemAddress;
		}

		public void setItemAddress(String itemAddress) {
			this.itemAddress = itemAddress;
		}

		public String getItemType() {
			return itemType;
		}

		public void setItemType(String itemType) {
			this.itemType = itemType;
		}

		public double getItemPrice() {
			return itemPrice;
		}

		public void setItemPrice(double itemPrice) {
			this.itemPrice = itemPrice;
		}

		public static OrderUserItem fromOrderUser(OrderUser order) {
			OrderUserItem orderUserItem = new OrderUserItem();
			if(order == null) {
				return orderUserItem;
			}
			orderUserItem.setUserName(order.getUserName());
			orderUserItem.setItemName(order.getItemName());
			orderUserItem.setQuantity(order.getQuantity());
			orderUserItem.setTransactionDate(order.getTransactionDate());
			return orderUserItem;
		}

		public static OrderUserItem fromOrderUserItem(OrderUser order, Item item) {
			OrderUserItem orderUserItem = fromOrderUser(order);
			if(item == null) {
				return orderUserItem;
			}
			orderUserItem.itemAddress = item.getAddress();
			orderUserItem.itemType = item.getType();
			orderUserItem.itemPrice = item.getPrice();
			return orderUserItem;
		}

		@Override
		public String toString() {
			return "OrderUserItem{" +
					"userName='" + userName + '\'' +
					", itemName='" + itemName + '\'' +
					", transactionDate=" + transactionDate +
					", quantity=" + quantity +
					", itemAddress='" + itemAddress + '\'' +
					", itemType='" + itemType + '\'' +
					", itemPrice=" + itemPrice +
					'}';
		}
	}

}
