package com.nuono.next.noonpull;

public interface NoonInterfacePullProvider {
    NoonInterfacePullPage fetchPage(NoonInterfacePullRequest request, int pageNumber);
}
