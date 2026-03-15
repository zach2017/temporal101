package com.example.temporaldemo.temporal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private final Temporal temporal = new Temporal();
    private String sharedFilesDir;

    public Temporal getTemporal() {
        return temporal;
    }

    public String getSharedFilesDir() {
        return sharedFilesDir;
    }

    public void setSharedFilesDir(String sharedFilesDir) {
        this.sharedFilesDir = sharedFilesDir;
    }

    public static class Temporal {
        private String address;
        private String namespace;
        private String workflowTaskQueue;
        private String javaActivityTaskQueue;
        private String pythonActivityTaskQueue;

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getWorkflowTaskQueue() {
            return workflowTaskQueue;
        }

        public void setWorkflowTaskQueue(String workflowTaskQueue) {
            this.workflowTaskQueue = workflowTaskQueue;
        }

        public String getJavaActivityTaskQueue() {
            return javaActivityTaskQueue;
        }

        public void setJavaActivityTaskQueue(String javaActivityTaskQueue) {
            this.javaActivityTaskQueue = javaActivityTaskQueue;
        }

        public String getPythonActivityTaskQueue() {
            return pythonActivityTaskQueue;
        }

        public void setPythonActivityTaskQueue(String pythonActivityTaskQueue) {
            this.pythonActivityTaskQueue = pythonActivityTaskQueue;
        }
    }
}
