package com.aacv.system.identity.domain;

public record UserProfile(
        String realName, String email, String phone, String organization, String department, String remark) {

    public static final UserProfile EMPTY = new UserProfile(null, null, null, null, null, null);

    public UserProfile {
        realName = normalize(realName, 64, "姓名", false);
        email = normalize(email, 254, "邮箱", false);
        phone = normalize(phone, 32, "联系电话", false);
        organization = normalize(organization, 128, "所属单位", false);
        department = normalize(department, 128, "部门/院系", false);
        remark = normalize(remark, 500, "备注", true);
        if (email != null && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("邮箱格式无效");
        }
        if (phone != null && (!phone.matches("\\+?[0-9() .-]+") || !phone.matches(".*[0-9].*"))) {
            throw new IllegalArgumentException("联系电话格式无效");
        }
    }

    private static String normalize(String value, int limit, String label, boolean multiline) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > limit || normalized.chars().anyMatch(
                c -> Character.isISOControl(c) && !(multiline && (c == '\n' || c == '\r' || c == '\t')))) {
            throw new IllegalArgumentException(label + "长度或字符无效");
        }
        return normalized;
    }
}
