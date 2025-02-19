package com.malskyi.studying.multithreading;

import com.malskyi.studying.multithreading.assembly_line.AssemblyDemo;
import lombok.Data;

@Data
public class Container {
    private String name;
    private ContainerStatus containerStatus;
    private String initializedBy;
    private String buildBy;
    private String deployedBy;

    public Container(int id) {
        this.name = String.format("Container-%s", id);
        this.containerStatus = ContainerStatus.EMPTY;
    }
}