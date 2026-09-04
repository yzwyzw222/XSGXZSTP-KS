package com.aacv.system.operations.application.port;

import java.util.OptionalLong;

public interface CurrentActorProvider {

    OptionalLong currentUserId();
}
