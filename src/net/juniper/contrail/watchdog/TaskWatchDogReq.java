package net.juniper.contrail.watchdog;

import java.net.URI;

public class TaskWatchDogReq {
    URI uri;
    // required params parsed from URI

    // optional params

    public TaskWatchDogReq(URI uri) {
        this.uri = uri;
    }
}
