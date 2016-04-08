package org.briarproject.android.util;

import android.app.Activity;
import android.graphics.Bitmap;
import android.util.DisplayMetrics;

import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;
import static com.google.zxing.BarcodeFormat.QR_CODE;

public class QrCodeUtils {

	public static Bitmap createQrCode(Activity activity, String input)
			throws WriterException {
		// Get narrowest screen dimension
		DisplayMetrics dm = new DisplayMetrics();
		activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
		int smallestDimen = Math.min(dm.widthPixels, dm.heightPixels);
		// Generate QR code
		BitMatrix encoded = new QRCodeWriter().encode(input, QR_CODE,
				smallestDimen, smallestDimen);
		// Convert QR code to Bitmap
		int width = encoded.getWidth();
		int height = encoded.getHeight();
		int[] pixels = new int[width * height];
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				pixels[y * width + x] = encoded.get(x, y) ? BLACK : WHITE;
			}
		}
		Bitmap qr = Bitmap.createBitmap(width, height, ARGB_8888);
		qr.setPixels(pixels, 0, width, 0, 0, width, height);
		return qr;
	}
}
