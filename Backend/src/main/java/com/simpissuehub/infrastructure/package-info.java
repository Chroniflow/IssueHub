/**
 * Infrastructure boundary for adapters that connect the application to
 * databases, mail providers, external stores, and runtime integrations.
 *
 * <p>Spring Data repositories remain in {@code repository} for now; new
 * provider-specific implementations should be placed in this package rather
 * than referenced directly by controllers.</p>
 */
package com.simpissuehub.infrastructure;
