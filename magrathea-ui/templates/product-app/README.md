# Magrathea Product Application Template

This product-neutral Vite application is both a buildable workspace fixture and a copyable starting point. It consumes `@magrathea/product-shell` through its public package boundary.

To copy it into another repository:

1. Copy `index.html`, `src`, `vite.config.ts`, and `tsconfig.json`.
2. Give the application package a product-owned name.
3. Depend on the released `@magrathea/product-shell` version recorded in `template.manifest.json`.
4. Register product-owned extensions in the application rather than modifying shell package sources.

The default page demonstrates the responsive frame, navigation, breadcrumbs, banners, cards, badges, a data table, standard states, and an accessible dialog form. Its sample content remains product-neutral and contains no product screen or API integration.
