package entity;

import javax.swing.*;
import java.util.Objects;

/**
 * @auth huyi
 * @date 2020/09/01 15:10
 * @Description
 */
public class Account {
    private Integer id;
    private Integer account;
    private Integer score;

    public Account() {
    }

    public Account(Integer id, Integer account, Integer score) {
        this.id = id;
        this.account = account;
        this.score = score;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getAccount() {
        return account;
    }

    public void setAccount(Integer account) {
        this.account = account;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account1 = (Account) o;
        return Objects.equals(id, account1.id) &&
                Objects.equals(account, account1.account) &&
                Objects.equals(score, account1.score);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, account, score);
    }

    @Override
    public String toString() {
        return "Account{" +
                "id=" + id +
                ", account=" + account +
                ", score=" + score +
                '}';
    }
}
