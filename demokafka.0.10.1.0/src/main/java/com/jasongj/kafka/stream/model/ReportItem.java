package com.jasongj.kafka.stream.model;

import com.jasongj.kafka.stream.PurchaseAnalysis3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReportItem {
    private String itemName;
    private String type;
    private long transactionDate;
    private int quantity;
    private double price;
    private double amount = -1;
    private List<RankItem> rankItemsByAccumulatedAmount = new ArrayList<>();
    private int rank;

    public void calculateRank() {
        Optional<RankItem> holder = rankItemsByAccumulatedAmount.stream().filter(ri -> this.itemName.equals(ri.getItemName())).findAny();
        if (holder.isPresent()) {
            holder.get().setAccumulatedAmount(holder.get().getAccumulatedAmount() + this.amount);
        } else {
            rankItemsByAccumulatedAmount.add(new RankItem(this.itemName, this.amount));
        }
        rankItemsByAccumulatedAmount.stream().sorted((a1, a2) -> Double.compare(a2.getAccumulatedAmount(), a1.getAccumulatedAmount()));
        for (int i=0; i<rankItemsByAccumulatedAmount.size();i++) {
            if (this.itemName.equals(rankItemsByAccumulatedAmount.get(i).getItemName())) {
                this.rank = i + 1;
            }
        }
    }

    public static ReportItem fromOrderUserItem(PurchaseAnalysis3.OrderUserItem orderUserItem) {
        ReportItem item = new ReportItem();
        item.itemName = orderUserItem.getItemName();
        item.type = orderUserItem.getItemType();
        item.quantity = orderUserItem.getQuantity();
        item.price = orderUserItem.getItemPrice();
        item.transactionDate = orderUserItem.getTransactionDate();
        return item;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public static class RankItem {
        private String itemName;
        private double accumulatedAmount;

        public RankItem(String itemName, double accumulatedAmount) {
            this.itemName = itemName;
            this.accumulatedAmount = accumulatedAmount;
        }

        public String getItemName() {
            return itemName;
        }

        public void setItemName(String itemName) {
            this.itemName = itemName;
        }

        public double getAccumulatedAmount() {
            return accumulatedAmount;
        }

        public void setAccumulatedAmount(double accumulatedAmount) {
            this.accumulatedAmount = accumulatedAmount;
        }
    }
    @Override
    public String toString() {
        return "ReportItem{" +
                "itemName='" + itemName + '\'' +
                ", type='" + type + '\'' +
                ", transactionDate=" + transactionDate +
                ", quantity=" + quantity +
                ", price=" + price +
                ", amount=" + amount +
                ", rank=" + rank +
                '}';
    }
}
