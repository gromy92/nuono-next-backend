package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccountType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductListingImageValidationServiceTest extends ProductListingServiceTest {
    @Test
    void fieldValidationReturnsDuplicatePskuAndBarcodeWithoutSavingDraft() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand command = validCommand();
        mapper.seedLocalProduct(
                10002L,
                "STR245027-NAE",
                "NN-TEST-PSKU",
                "6290000000001",
                88001L,
                null
        );

        ProductListingFieldValidationView view = service.validateFields(context, command);

        assertIssue(view.getIssues(), "psku", "partner_sku_already_exists");
        assertIssue(view.getIssues(), "barcode", "barcode_already_exists");
        assertEquals(null, mapper.insertedDraft());
        assertEquals(0, mapper.updateCount());
    }

}
