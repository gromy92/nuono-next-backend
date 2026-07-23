# Packing-list templates

These sanitized templates preserve the layouts supplied by operations while removing historical order data and product images.

- `yite-packing-list.xlsx`: YT/义特, one worksheet containing all boxes. Repeated box numbers represent multiple products in one box.
- `zd-packing-list.xlsx`: ZD/众鸫, one prototype worksheet. The exporter clones it once per box.

The exporter fills only authoritative warehouse data: batch number, box number, dimensions, gross weight, PSKU, Chinese product title, quantity, and available product images. Fields without a reliable system source remain blank.
