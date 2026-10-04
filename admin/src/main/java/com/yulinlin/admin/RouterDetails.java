package com.yulinlin.admin;

import com.yulinlin.data.core.anno.JoinQuery;

import java.util.List;

public class RouterDetails {

    private String username;

    private String loginType;

    @JoinQuery(primary = "username",value = "username")
    private SysUserEntity user;

    @JoinQuery(primary = "id",value = "user.sysRoleIds")
    private List<SysRoleEntity> roles;

    @JoinQuery(primary = "id",value = "roles.sysMenuIds")
    private List<SysMenuEntity> menus;

    public RouterDetails(String username, String loginType) {
        this.username = username;
        this.loginType = loginType;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getLoginType() {
        return loginType;
    }

    public void setLoginType(String loginType) {
        this.loginType = loginType;
    }

    public SysUserEntity getUser() {
        return user;
    }

    public void setUser(SysUserEntity user) {
        this.user = user;
    }

    public List<SysRoleEntity> getRoles() {
        return roles;
    }

    public void setRoles(List<SysRoleEntity> roles) {
        this.roles = roles;
    }

    public List<SysMenuEntity> getMenus() {
        return menus;
    }

    public void setMenus(List<SysMenuEntity> menus) {
        this.menus = menus;
    }
}
