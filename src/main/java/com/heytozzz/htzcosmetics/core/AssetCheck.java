package com.heytozzz.htzcosmetics.core;

import com.heytozzz.htzcosmetics.core.crypto.Hashes;
import com.heytozzz.htzcosmetics.core.manifest.AssetRef;
import com.heytozzz.htzcosmetics.core.model.CosmeticModel;
import com.heytozzz.htzcosmetics.core.model.ModelParser;
import com.heytozzz.htzcosmetics.core.png.DecodedImage;
import com.heytozzz.htzcosmetics.core.png.SafePngDecoder;

/**
 * The one place that turns downloaded (or cached) bytes into usable data.
 * Order matters: exact size, then SHA-256 from the signed manifest, and only then parsing.
 */
public final class AssetCheck {
    private AssetCheck() {
    }

    public static CosmeticModel model(byte[] bytes, AssetRef ref) throws ValidationException {
        checkIntegrity(bytes, ref);
        return ModelParser.parse(bytes);
    }

    public static DecodedImage texture(byte[] bytes, AssetRef ref) throws ValidationException {
        checkIntegrity(bytes, ref);
        return SafePngDecoder.decode(bytes);
    }

    public static void checkIntegrity(byte[] bytes, AssetRef ref) throws ValidationException {
        if (bytes == null || bytes.length != ref.size()) throw new ValidationException("asset size does not match the manifest");
        if (!Hashes.matches(bytes, ref.sha256())) throw new ValidationException("asset SHA-256 does not match the manifest");
    }
}
