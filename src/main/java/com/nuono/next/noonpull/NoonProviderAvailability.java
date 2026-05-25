package com.nuono.next.noonpull;

@FunctionalInterface
public interface NoonProviderAvailability {
    boolean isAvailable(NoonPullPlanRecord plan);
}
