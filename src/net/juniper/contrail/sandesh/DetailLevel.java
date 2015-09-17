package net.juniper.contrail.sandesh;

public enum DetailLevel {
    REGULAR, // default
    BRIEF, // include keys and required fields
    PARENT, // fields inherited from the parent
            // that will not be displayed when the parent is displayed
    FULL    // include less important fields that will be displayed
            // only in detailed view
}
