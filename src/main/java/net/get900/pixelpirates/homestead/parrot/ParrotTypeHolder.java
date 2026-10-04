package net.get900.pixelpirates.homestead.parrot;

/** Implemented on ParrotEntity by ParrotTypeMixin: the custom parrot type id ("" = a plain vanilla colour). */
public interface ParrotTypeHolder {
    String pixelpirates$getParrotType();

    void pixelpirates$setParrotType(String id);

    /** Client side, while the shoulder renderer draws a parrot from its saved data: that parrot's type, else null. */
    ThreadLocal<String> SHOULDER_TYPE = new ThreadLocal<>();
}
