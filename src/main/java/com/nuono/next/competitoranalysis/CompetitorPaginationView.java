package com.nuono.next.competitoranalysis;

public class CompetitorPaginationView {
    private int page;
    private int pageSize;
    private long total;

    public static CompetitorPaginationView of(CompetitorWatchProductQuery query, long total) {
        CompetitorPaginationView view = new CompetitorPaginationView();
        view.setPage(query == null ? 1 : query.getPage());
        view.setPageSize(query == null ? 20 : query.getPageSize());
        view.setTotal(total);
        return view;
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}
