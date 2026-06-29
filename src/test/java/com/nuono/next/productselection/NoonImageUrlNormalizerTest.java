package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class NoonImageUrlNormalizerTest {

    @Test
    void normalizeConvertsNoonHashedPzskuAssetKeyToCdnImageUrl() {
        assertEquals(
                "https://f.nooncdn.com/p/eff639f2df2651369082d90705ccc7ca%7Cpzsku/Z72E210340BAE3D7BC083Z/45/1768470807/02b50050-583d-484e-bfc5-639dcdcf4201.jpg",
                NoonImageUrlNormalizer.normalize(
                        "https://f.nooncdn.com/eff639f2df2651369082d90705ccc7ca|pzsku/Z72E210340BAE3D7BC083Z/45/1768470807/02b50050-583d-484e-bfc5-639dcdcf4201"
                )
        );
        assertEquals(
                "https://f.nooncdn.com/p/eff639f2df2651369082d90705ccc7ca%7Cpzsku/Z72E210340BAE3D7BC083Z/45/1768470807/02b50050-583d-484e-bfc5-639dcdcf4201.jpg",
                NoonImageUrlNormalizer.normalize(
                        "https://f.nooncdn.com/eff639f2df2651369082d90705ccc7ca%7Cpzsku/Z72E210340BAE3D7BC083Z/45/1768470807/02b50050-583d-484e-bfc5-639dcdcf4201"
                )
        );
    }

    @Test
    void normalizeConvertsBareNoonPnskuPathToCdnImageUrlAndPreservesQueryString() {
        assertEquals(
                "https://f.nooncdn.com/p/pnsku/N13036202A/45/_/1767608204/990f8be8-6829-401e-ad3d-ddc34fecc6f0.jpg?format=jpg&width=240",
                NoonImageUrlNormalizer.normalize(
                        "pnsku/N13036202A/45/_/1767608204/990f8be8-6829-401e-ad3d-ddc34fecc6f0?format=jpg&width=240"
                )
        );
    }

    @Test
    void normalizeKeepsExistingCdnImageUrlStable() {
        assertEquals(
                "https://f.nooncdn.com/p/pzsku/Z92550AC9ECB3A39E5B7AZ/45/1768272072/75cf5a38-3af3-4055-ae03-df18f1d3912b.jpg",
                NoonImageUrlNormalizer.normalize(
                        "https://f.nooncdn.com/pzsku/Z92550AC9ECB3A39E5B7AZ/45/1768272072/75cf5a38-3af3-4055-ae03-df18f1d3912b"
                )
        );
        assertNull(NoonImageUrlNormalizer.normalize(" "));
    }
}
