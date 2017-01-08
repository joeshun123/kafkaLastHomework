package com.jasongj.kafka.stream.model;

public class UserAddressGenderKey {
    private String userAddress;
    private String gender;
    public UserAddressGenderKey() {
    }
    public UserAddressGenderKey(String userAddress, String gender) {
        this.userAddress = userAddress;
        this.gender = gender;
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

    @Override
    public String toString() {
        return "userAddress:" + this.userAddress  + " gender:" + this.gender;
    }
}
