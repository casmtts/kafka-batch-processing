package com.example.kafkabatch;

import java.math.BigDecimal;

public final class ProductValidator {
    private ProductValidator() {
    }

    public static ValidationResult validate(ProductEvent event) {
        if (event.columnCount() != 4) {
            return new ValidationResult(null, "A linha deve conter quatro colunas.");
        }
        String sku = event.sku().trim();
        String name = event.name().trim();
        if (sku.isEmpty() || sku.length() > 100) {
            return new ValidationResult(null, "SKU obrigatório e limitado a 100 caracteres.");
        }
        if (name.isEmpty() || name.length() > 255) {
            return new ValidationResult(null, "Nome obrigatório e limitado a 255 caracteres.");
        }
        BigDecimal price;
        try {
            price = new BigDecimal(event.price().trim());
            if (price.signum() < 0 || price.scale() > 2 || price.precision() - price.scale() > 16) {
                return new ValidationResult(null, "Preço deve ser não negativo e ter até duas casas decimais.");
            }
        } catch (NumberFormatException ex) {
            return new ValidationResult(null, "Preço inválido.");
        }
        int stock;
        try {
            stock = Integer.parseInt(event.stock().trim());
            if (stock < 0) {
                return new ValidationResult(null, "Estoque deve ser não negativo.");
            }
        } catch (NumberFormatException ex) {
            return new ValidationResult(null, "Estoque inválido.");
        }
        return new ValidationResult(new ValidProduct(sku, name, price, stock), null);
    }

    public record ValidProduct(String sku, String name, BigDecimal price, int stock) {
    }

    public record ValidationResult(ValidProduct product, String error) {
    }
}
