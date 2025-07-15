package com.dahe.v2.modules.user.service;

import com.dahe.v2.modules.user.model.AppUser;

/**
 * 用户领域写服务。
 *
 * <p>集中承接 user 领域关键写入规则，避免在上层应用服务中散落 set 字段逻辑。</p>
 */
public interface UserDomainService {

    /**
     * 小程序登录时创建或更新用户，并按规则处理审核状态迁移。
     */
    MiniappLoginUpsertResult upsertMiniappUserFromLogin(MiniappLoginCommand command);

    /**
     * 审核小程序用户（通过/拒绝）。
     */
    AppUser applyMiniappReviewDecision(AppUser user, boolean approve, Boolean canConsole, String rejectReason);

    /**
     * 更新小程序用户控制台能力（小程序不参与角色体系）。
     */
    AppUser applyMiniappConsole(AppUser user, Boolean canConsole);

    /**
     * 更新后台用户角色与控制台能力。
     */
    AppUser applyAdminRoleAndConsole(AppUser user, String roleCode, Boolean canConsole);

    /**
     * 更新用户可用性。
     */
    AppUser applyEnabled(AppUser user, boolean enabled);

    /**
     * 更新小程序用户业务状态。
     */
    AppUser applyMiniappStatus(AppUser user, String status);

    /**
     * 创建后台用户。
     */
    AppUser createAdminUser(AdminCreateCommand command);

    /**
     * 小程序登录写入参数。
     */
    class MiniappLoginCommand {
        private String wxOpenId;
        private String nickName;
        private String realName;
        private String phone;
        private String applyReason;
        private String avatarUrl;
        private String wxAvatarUrl;
        private String avatarSource;

        public String getWxOpenId() {
            return wxOpenId;
        }

        public void setWxOpenId(String wxOpenId) {
            this.wxOpenId = wxOpenId;
        }

        public String getNickName() {
            return nickName;
        }

        public void setNickName(String nickName) {
            this.nickName = nickName;
        }

        public String getRealName() {
            return realName;
        }

        public void setRealName(String realName) {
            this.realName = realName;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getApplyReason() {
            return applyReason;
        }

        public void setApplyReason(String applyReason) {
            this.applyReason = applyReason;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }

        public String getWxAvatarUrl() {
            return wxAvatarUrl;
        }

        public void setWxAvatarUrl(String wxAvatarUrl) {
            this.wxAvatarUrl = wxAvatarUrl;
        }

        public String getAvatarSource() {
            return avatarSource;
        }

        public void setAvatarSource(String avatarSource) {
            this.avatarSource = avatarSource;
        }
    }

    /**
     * 后台用户创建参数。
     */
    class AdminCreateCommand {
        private String wxOpenId;
        private String loginName;
        private String passwordHash;
        private String realName;
        private String nickName;
        private String phone;
        private String roleCode;
        private Boolean canConsole;
        private String avatarUrl;

        public String getWxOpenId() {
            return wxOpenId;
        }

        public void setWxOpenId(String wxOpenId) {
            this.wxOpenId = wxOpenId;
        }

        public String getRealName() {
            return realName;
        }

        public String getLoginName() {
            return loginName;
        }

        public void setLoginName(String loginName) {
            this.loginName = loginName;
        }

        public String getPasswordHash() {
            return passwordHash;
        }

        public void setPasswordHash(String passwordHash) {
            this.passwordHash = passwordHash;
        }

        public void setRealName(String realName) {
            this.realName = realName;
        }

        public String getNickName() {
            return nickName;
        }

        public void setNickName(String nickName) {
            this.nickName = nickName;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getRoleCode() {
            return roleCode;
        }

        public void setRoleCode(String roleCode) {
            this.roleCode = roleCode;
        }

        public Boolean getCanConsole() {
            return canConsole;
        }

        public void setCanConsole(Boolean canConsole) {
            this.canConsole = canConsole;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }
    }

    /**
     * 小程序登录 upsert 结果。
     */
    class MiniappLoginUpsertResult {
        private AppUser user;
        private boolean reviewApplied;
        private boolean reApply;

        public AppUser getUser() {
            return user;
        }

        public void setUser(AppUser user) {
            this.user = user;
        }

        public boolean isReviewApplied() {
            return reviewApplied;
        }

        public void setReviewApplied(boolean reviewApplied) {
            this.reviewApplied = reviewApplied;
        }

        public boolean isReApply() {
            return reApply;
        }

        public void setReApply(boolean reApply) {
            this.reApply = reApply;
        }
    }
}
