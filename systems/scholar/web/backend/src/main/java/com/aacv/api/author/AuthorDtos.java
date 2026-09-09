package com.aacv.api.author;

import java.util.List;

/**
 * 作者检索接口 DTO 集合。
 */
public final class AuthorDtos {

    private AuthorDtos() {
    }

    /** 作者条目：id + 姓名 + 论文数。 */
    public record AuthorItem(String id, String name, long paperCount) {
    }
}
