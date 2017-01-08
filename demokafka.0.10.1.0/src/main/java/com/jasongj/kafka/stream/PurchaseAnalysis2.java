package com.jasongj.kafka.stream;

import com.jasongj.kafka.stream.model.Item;
import com.jasongj.kafka.stream.model.Order;
import com.jasongj.kafka.stream.model.User;
import com.jasongj.kafka.stream.model.UserAddressGenderKey;
import com.jasongj.kafka.stream.serdes.SerdesFactory;
import com.jasongj.kafka.stream.timeextractor.OrderTimestampExtractor;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KStreamBuilder;
import org.apache.kafka.streams.kstream.KTable;

import java.io.IOException;
import java.util.Properties;

public class PurchaseAnalysis2 {

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
		//itemTable.toStream().foreach((String itemName, Item item) -> System.out.printf("Item info %s-%s-%s-%s\n", item.getItemName(), item.getAddress(), item.getType(), item.getPrice()));
		KStream<UserAddressGenderKey, OrderUserItem> baseTable = orderStream
				.leftJoin(userTable, (Order order, User user) -> OrderUser.fromOrderUser(order, user), Serdes.String(), SerdesFactory.serdFrom(Order.class))
				.filter((String userName, OrderUser orderUser) -> orderUser.userAddress != null)
				.map((String userName, OrderUser orderUser) -> new KeyValue<String, OrderUser>(orderUser.itemName, orderUser))
				.through(Serdes.String(), SerdesFactory.serdFrom(OrderUser.class), (String key, OrderUser orderUser, int numPartitions) -> (orderUser.getItemName().hashCode() & 0x7FFFFFFF) % numPartitions, "orderuser-repartition-by-item")
				.leftJoin(itemTable, (OrderUser orderUser, Item item) -> OrderUserItem.fromOrderUser(orderUser, item), Serdes.String(), SerdesFactory.serdFrom(OrderUser.class))
				.map((String itemName, OrderUserItem orderUserItem)
						-> new KeyValue<>(new UserAddressGenderKey(orderUserItem.userAddress, orderUserItem.gender), orderUserItem))
				.through(SerdesFactory.serdFrom(UserAddressGenderKey.class), SerdesFactory.serdFrom(OrderUserItem.class),
						(UserAddressGenderKey key, OrderUserItem orderUser, int numPartitions) -> (orderUser.getItemName().hashCode() & 0x7FFFFFFF) % numPartitions, "orderuser-repartition-by-item-1");

		KTable<UserAddressGenderKey, Double> amountTable = baseTable
				.map((UserAddressGenderKey key, OrderUserItem orderUserItem) -> KeyValue.pair(key, (Double) (orderUserItem.quantity * orderUserItem.itemPrice)))
				.groupByKey(SerdesFactory.serdFrom(UserAddressGenderKey.class), Serdes.Double())
				.reduce((Double v1, Double v2) -> v1 + v2, "amount-state-store");
		//amountTable.foreach((key, amount) -> System.out.println("Amount=>" + key.toString() + " " + amount));

		KTable<UserAddressGenderKey, Integer> orderCountTable = baseTable
				.map((UserAddressGenderKey key, OrderUserItem orderUserItem) -> KeyValue.pair(key, 1))
				.groupByKey(SerdesFactory.serdFrom(UserAddressGenderKey.class), Serdes.Integer())
				.reduce((Integer v1, Integer v2) -> v1 + v2, "orderCount-state-store");
		//orderCountTable.foreach((key, orderCount) -> System.out.println("orderCount=>" + key.toString() + " " + orderCount));

		KTable<UserAddressGenderKey, Integer> sumQtyTable = baseTable
				.map((UserAddressGenderKey key, OrderUserItem orderUserItem) -> KeyValue.pair(key, (Integer) (orderUserItem.quantity)))
				.groupByKey(SerdesFactory.serdFrom(UserAddressGenderKey.class), Serdes.Integer())
				.reduce((Integer v1, Integer v2) -> v1 + v2, "sumQty-state-store");
		//sumQtyTable.foreach((key, sumQty) -> System.out.println("sumQty=>" + key.toString() + " " + sumQty));

		KTable<UserAddressGenderKey, Result> resultTable  = amountTable
				.leftJoin(orderCountTable, (amount, orderCount) -> new Result(amount, orderCount))
				.leftJoin(sumQtyTable, (result, sumQty) -> {
					result.sumQty = sumQty;
					return result;
				});
		resultTable.foreach((key, result) -> System.out.println(key.toString() + " " + result.toString()));

		resultTable.toStream()
				.map((key, result) -> new KeyValue<>(key.toString(), result.toString()))
				.to("gender-amount");

		KafkaStreams kafkaStreams = new KafkaStreams(streamBuilder, props);
		kafkaStreams.cleanUp();
		kafkaStreams.start();
		
		System.in.read();
		kafkaStreams.close();
		kafkaStreams.cleanUp();
	}
	public static class Result {
		private Double amount;
		private Integer orderCount;
		private Integer sumQty;
		public Result(Double amount, Integer orderCount) {
			this.amount = amount;
			this.orderCount = orderCount;
		}

		@Override
		public String toString() {
			return "orderCount:" + orderCount + " sumQty:" + sumQty + " amount=" +  amount;
		}
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
	}
	
	public static class OrderUserItem {
		private String userName;
		private String itemName;
		private long transactionDate;
		private int quantity;
		private String userAddress;
		private String gender;
		private int age;
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

		public static OrderUserItem fromOrderUser(OrderUser orderUser) {
			OrderUserItem orderUserItem = new OrderUserItem();
			if(orderUser == null) {
				return orderUserItem;
			}
			orderUserItem.userName = orderUser.userName;
			orderUserItem.itemName = orderUser.itemName;
			orderUserItem.transactionDate = orderUser.transactionDate;
			orderUserItem.quantity = orderUser.quantity;
			orderUserItem.userAddress = orderUser.userAddress;
			orderUserItem.gender = orderUser.gender;
			orderUserItem.age = orderUser.age;
			return orderUserItem;
		}

		public static OrderUserItem fromOrderUser(OrderUser orderUser, Item item) {
			OrderUserItem orderUserItem = fromOrderUser(orderUser);
			if(item == null) {
				return orderUserItem;
			}
			orderUserItem.itemAddress = item.getAddress();
			orderUserItem.itemType = item.getType();
			orderUserItem.itemPrice = item.getPrice();
			return orderUserItem;
		}
	}

}
