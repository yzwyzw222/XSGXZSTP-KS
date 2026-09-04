package com.aacv.system.export.application.port;

public interface ExportActorProvider {
    ExportActor current();

    record ExportActor(long userId, boolean administrator) {
    }
}
