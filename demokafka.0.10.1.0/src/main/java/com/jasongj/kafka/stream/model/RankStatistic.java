package com.jasongj.kafka.stream.model;

public class RankStatistic {
    private String itemName;
    private double accumulatedAmount;
    private String type;
    private int rank;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public static RankStatistic fromAmountItem(Double amount, Item item) {
        RankStatistic rankStatistic = new RankStatistic();
        rankStatistic.setAccumulatedAmount(amount);
        rankStatistic.setItemName(item.getItemName());
        rankStatistic.setType(item.getType());
        return rankStatistic;
    }
}
