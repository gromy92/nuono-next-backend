package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.product.publish.ProductPublishCommandService;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductPublishTaskViewBuilderTest {

    private ProductPublishCommandService productPublishCommandService;
    private Function<ProductPublishTaskRecord, ProductMasterWorkbenchView> terminalWorkbenchBuilder;
    private Function<ProductPublishTaskRecord, List<String>> changedDomainsResolver;
    private ProductPublishTaskViewBuilder builder;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        productPublishCommandService = mock(ProductPublishCommandService.class);
        terminalWorkbenchBuilder = mock(Function.class);
        changedDomainsResolver = mock(Function.class);
        builder = new ProductPublishTaskViewBuilder(
                productPublishCommandService,
                terminalWorkbenchBuilder,
                changedDomainsResolver
        );
    }

    @Test
    void buildDelegatesToPublishCommandServiceWithCallbacks() {
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(77001L);
        ProductPublishTaskView expected = new ProductPublishTaskView();
        expected.setTaskId(77001L);
        when(productPublishCommandService.buildTaskView(
                eq(task),
                eq(false),
                same(terminalWorkbenchBuilder),
                same(changedDomainsResolver)
        )).thenReturn(expected);

        ProductPublishTaskView actual = builder.build(task, false);

        assertSame(expected, actual);
    }

    @Test
    void loadDelegatesToPublishCommandServiceWithCallbacks() {
        ProductPublishTaskView expected = new ProductPublishTaskView();
        expected.setTaskId(77001L);
        when(productPublishCommandService.loadTask(
                eq(77001L),
                eq(10002L),
                same(terminalWorkbenchBuilder),
                same(changedDomainsResolver)
        )).thenReturn(expected);

        ProductPublishTaskView actual = builder.load(77001L, 10002L);

        assertSame(expected, actual);
    }

    @Test
    void retryDelegatesToPublishCommandServiceWithCallbacks() {
        ProductPublishTaskView expected = new ProductPublishTaskView();
        expected.setTaskId(77001L);
        when(productPublishCommandService.retryTask(
                eq(77001L),
                eq(10002L),
                same(terminalWorkbenchBuilder),
                same(changedDomainsResolver)
        )).thenReturn(expected);

        ProductPublishTaskView actual = builder.retry(77001L, 10002L);

        assertSame(expected, actual);
    }

    @Test
    void cancelDelegatesToPublishCommandServiceWithCallbacks() {
        ProductPublishTaskView expected = new ProductPublishTaskView();
        expected.setTaskId(77001L);
        when(productPublishCommandService.cancelTask(
                eq(77001L),
                eq(10002L),
                same(terminalWorkbenchBuilder),
                same(changedDomainsResolver)
        )).thenReturn(expected);

        ProductPublishTaskView actual = builder.cancel(77001L, 10002L);

        assertSame(expected, actual);
    }
}
