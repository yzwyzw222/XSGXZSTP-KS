package com.aacv.system.graph.infrastructure.persistence;

import com.aacv.system.graph.domain.GraphMaintenanceStatus;
import com.aacv.system.graph.domain.GraphMaintenanceType;

public class GraphMaintenanceRow {

    private Long id;
    private GraphMaintenanceType runType;
    private GraphMaintenanceStatus status;
    private long requestedBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public GraphMaintenanceType getRunType() {
        return runType;
    }

    public void setRunType(GraphMaintenanceType runType) {
        this.runType = runType;
    }

    public GraphMaintenanceStatus getStatus() {
        return status;
    }

    public void setStatus(GraphMaintenanceStatus status) {
        this.status = status;
    }

    public long getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(long requestedBy) {
        this.requestedBy = requestedBy;
    }
}
