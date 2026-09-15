// Composited image bytes, for the JVM and Android bindings.
//
// pdfium can render an image object with its soft mask applied, which is the only way to get a
// figure's transparency: a PDF keeps it in a separate mask stream, so the image's own data has black
// where the page shows nothing. Without this, every masked figure arrives on a black rectangle.
//
// It cannot be done through the generated bridges. FPDFBitmap_GetBuffer returns a raw address, which
// the generator passes as a jlong the JVM has no way to read, and FPDFBitmap_CreateEx would take a
// buffer the bridge releases before pdfium is finished writing to it — the same use-after-free the
// document loader had. Both pointers stay inside this function instead, and only bytes cross out.

#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include "fpdfview.h"
#include "fpdf_edit.h"

// A page is a few thousand pixels a side at most; anything larger is a malformed stream.
#define MAX_PIXELS (64 * 1024 * 1024 / 4)

JNIEXPORT jbyteArray JNICALL
Java_io_github_lemcoder_mikromarkdown_pdf_Masks_renderedRgba(JNIEnv* env,
                                                             jclass clazz,
                                                             jlong document,
                                                             jlong page,
                                                             jlong image_object,
                                                             jintArray size_out) {
    (void)clazz;

    FPDF_BITMAP bitmap = FPDFImageObj_GetRenderedBitmap((FPDF_DOCUMENT)document,
                                                        (FPDF_PAGE)page,
                                                        (FPDF_PAGEOBJECT)image_object);
    if (!bitmap) {
        return NULL;
    }

    const int width = FPDFBitmap_GetWidth(bitmap);
    const int height = FPDFBitmap_GetHeight(bitmap);
    const int stride = FPDFBitmap_GetStride(bitmap);
    const int format = FPDFBitmap_GetFormat(bitmap);
    const unsigned char* pixels = (const unsigned char*)FPDFBitmap_GetBuffer(bitmap);

    // BGRA is what a mask composite produces; anything else is not something to reinterpret blindly.
    if (!pixels || width <= 0 || height <= 0 || format != FPDFBitmap_BGRA ||
        (long)width * height > MAX_PIXELS) {
        FPDFBitmap_Destroy(bitmap);
        return NULL;
    }

    jbyteArray out = (*env)->NewByteArray(env, width * height * 4);
    if (!out) {
        FPDFBitmap_Destroy(bitmap);
        return NULL;
    }

    // One row at a time: the rows are strided, and the caller wants them packed and in RGBA order.
    jbyte* row = (jbyte*)malloc((size_t)width * 4);
    if (!row) {
        FPDFBitmap_Destroy(bitmap);
        return NULL;
    }
    for (int y = 0; y < height; y++) {
        const unsigned char* source = pixels + (size_t)y * stride;
        for (int x = 0; x < width; x++) {
            row[x * 4 + 0] = (jbyte)source[x * 4 + 2];
            row[x * 4 + 1] = (jbyte)source[x * 4 + 1];
            row[x * 4 + 2] = (jbyte)source[x * 4 + 0];
            row[x * 4 + 3] = (jbyte)source[x * 4 + 3];
        }
        (*env)->SetByteArrayRegion(env, out, y * width * 4, width * 4, row);
    }
    free(row);
    FPDFBitmap_Destroy(bitmap);

    if (size_out) {
        jint size[2] = {width, height};
        (*env)->SetIntArrayRegion(env, size_out, 0, 2, size);
    }
    return out;
}
