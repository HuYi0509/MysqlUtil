package entity;

import java.util.Objects;

/**
 * @auth huyi
 * @date 2020/09/01 15:08
 * @Description
 */
public class User {
    private Integer id;
    private String  user_name;
    private String  account_id;

    public User() {
    }

    public User(Integer id, String user_name, String account_id) {
        this.id = id;
        this.user_name = user_name;
        this.account_id = account_id;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUser_name() {
        return user_name;
    }

    public void setUser_name(String user_name) {
        this.user_name = user_name;
    }

    public String getAccount_id() {
        return account_id;
    }

    public void setAccount_id(String account_id) {
        this.account_id = account_id;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", user_name='" + user_name + '\'' +
                ", account_id='" + account_id + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) &&
                Objects.equals(user_name, user.user_name) &&
                Objects.equals(account_id, user.account_id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, user_name, account_id);
    }
}
