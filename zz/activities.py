# ==========================================
# FILE: activities/file_ops.py
# ==========================================
import os
from tika import detector
from temporalio import activity

# Tika server URL from environment variable (Docker compose)
TIKA_SERVER_ENDPOINT = os.getenv("TIKA_SERVER_ENDPOINT", "http://tika:9998")

@activity.defn
async def detect_mime_type(file_path: str) -> str:
    """Detects MIME type using Tika Server."""
    activity.logger.info(f"Detecting MIME type for {file_path}")
    # tika-python handles the REST call to the standalone server
    mime = detector.from_file(file_path, serverEndpoint=TIKA_SERVER_ENDPOINT)
    return mime

@activity.defn
async def save_text_to_file(data: dict) -> str:
    """Saves extracted text to a file named [docname].txt"""
    original_filepath = data["original_filepath"]
    text_content = data["text_content"]
    
    base_name = os.path.splitext(os.path.basename(original_filepath))[0]
    output_filename = f"{base_name}.txt"
    
    activity.logger.info(f"Saving extracted text to {output_filename}")
    with open(output_filename, "w", encoding="utf-8") as f:
        f.write(text_content)
        
    return output_filename

@activity.defn
async def save_image_text_to_file(data: dict) -> str:
    """Saves OCR text from PDF image to [docname]_image[n].txt"""
    original_filepath = data["original_filepath"]
    image_index = data["image_index"]
    text_content = data["text_content"]
    
    base_name = os.path.splitext(os.path.basename(original_filepath))[0]
    output_filename = f"{base_name}_image_{image_index}.txt"
    
    activity.logger.info(f"Saving OCR text to {output_filename}")
    with open(output_filename, "w", encoding="utf-8") as f:
        f.write(text_content)
        
    return output_filename


# ==========================================
# FILE: activities/pdf_ops.py
# ==========================================
import fitz  # PyMuPDF
from temporalio import activity
from typing import Generator, Tuple, List

@activity.defn
async def extract_pdf_text_stream(file_path: str) -> str:
    """
    Extracts text from PDF page-by-page to be memory efficient.
    Returns the accumulated text.
    """
    activity.logger.info(f"Streaming text extraction from PDF: {file_path}")
    full_text = ""
    with fitz.open(file_path) as doc:
        for page in doc:
            # yield text page by page if the SDK supported generator return, 
            # but for simplicity in activity we return full text of large doc.
            # PyMuPDF itself is very efficient.
            full_text += page.get_text() + "\n--- Page Break ---\n"
    return full_text

@activity.defn
async def extract_pdf_images(file_path: str) -> List[dict]:
    """
    Extracts images from PDF. 
    Returns a list of image metadata and bytes to be passed to OCR.
    """
    activity.logger.info(f"Extracting images from PDF: {file_path}")
    extracted_images_data = []
    
    with fitz.open(file_path) as doc:
        for page_index, page in enumerate(doc):
            image_list = page.get_images(full=True)
            
            for img_index, img in enumerate(image_list):
                xref = img[0]
                base_image = doc.extract_image(xref)
                image_bytes = base_image["image"]
                image_ext = base_image["ext"]
                
                # We return bytes to avoid saving temporary files between activities.
                extracted_images_data.append({
                    "original_filepath": file_path,
                    "page_index": page_index,
                    "image_index": f"{page_index}_{img_index}",
                    "image_bytes": image_bytes,
                    "extension": image_ext
                })
    return extracted_images_data


# ==========================================
# FILE: activities/ocr_ops.py
# ==========================================
import pytesseract
from PIL import Image
import io
from temporalio import activity
from typing import Generator

# We assume tesseract binary is in the PATH (handled by Dockerfile)

@activity.defn
async def perform_ocr_on_image_bytes(image_data: dict) -> dict:
    """
    Performs Tesseract OCR on image bytes.
    Returns the text and original metadata.
    """
    image_bytes = image_data["image_bytes"]
    activity.logger.info(f"Performing OCR on image {image_data['image_index']} from {image_data['original_filepath']}")
    
    # Load image from bytes efficiently
    image = Image.open(io.BytesIO(image_bytes))
    
    # Perform OCR
    text = pytesseract.image_to_string(image)
    
    return {
        "original_filepath": image_data["original_filepath"],
        "image_index": image_data["image_index"],
        "text_content": text
    }


# ==========================================
# FILE: workflows.py
# ==========================================
from datetime import timedelta
from temporalio import workflow
from temporalio.common import RetryPolicy

# Import activity definitions for type safety
with workflow.unsafe.imports_passed_through():
    from activities.file_ops import detect_mime_type, save_text_to_file, save_image_text_to_file
    from activities.pdf_ops import extract_pdf_text_stream, extract_pdf_images
    from activities.ocr_ops import perform_ocr_on_image_bytes

@workflow.defn
class DocumentTextExtractionWorkflow:
    @workflow.run
    async def run(self, file_path: str) -> str:
        # Standard retry policy for IO-bound activities
        standard_retry = RetryPolicy(
            initial_interval=timedelta(seconds=1),
            maximum_interval=timedelta(seconds=10),
            maximum_attempts=3
        )

        # 1. Detect MIME type
        mime_type = await workflow.execute_activity(
            detect_mime_type,
            file_path,
            start_to_close_timeout=timedelta(seconds=30),
            retry_policy=standard_retry
        )
        
        extracted_files = []

        # 2. Branch based on MIME
        if mime_type == "application/pdf":
            workflow.logger.info("PDF detected. Starting specialized extraction.")
            
            # 2a. Extract PDF Text (Memory Efficient within Activity)
            pdf_text = await workflow.execute_activity(
                extract_pdf_text_stream,
                file_path,
                start_to_close_timeout=timedelta(minutes=5),
                retry_policy=standard_retry
            )
            
            # 2b. Save PDF Text
            saved_doc = await workflow.execute_activity(
                save_text_to_file,
                {"original_filepath": file_path, "text_content": pdf_text},
                start_to_close_timeout=timedelta(seconds=30)
            )
            extracted_files.append(saved_doc)
            
            # 2c. Extract Images from PDF
            images_data = await workflow.execute_activity(
                extract_pdf_images,
                file_path,
                start_to_close_timeout=timedelta(minutes=10), # Might be slow for big PDFs
                retry_policy=standard_retry
            )
            
            # 2d. Perform OCR on each extracted image
            # We use workflow.execute_activity in a loop, but in production,
            # you would use asyncio.gather to parallelize these across workers.
            for img_info in images_data:
                ocr_result = await workflow.execute_activity(
                    perform_ocr_on_image_bytes,
                    img_info,
                    start_to_close_timeout=timedelta(minutes=2),
                    retry_policy=RetryPolicy(maximum_attempts=2) # Less retries for heavy OCR
                )
                
                # 2e. Save OCR Text
                saved_img_text = await workflow.execute_activity(
                    save_image_text_to_file,
                    ocr_result,
                    start_to_close_timeout=timedelta(seconds=30)
                )
                extracted_files.append(saved_img_text)
                
        elif mime_type.startswith("image/"):
            workflow.logger.info("Generic image detected. Starting OCR.")
            # Similar flow to PDF images, but need a file_to_bytes activity first (omitted here)
            pass
            
        else:
            workflow.logger.warn(f"Unsupported MIME type: {mime_type}. Calling generic Tika Text extraction (omitted).")
            # You would call a generic tika.parser activity here
            pass

        return f"Extraction complete. Files saved: {', '.join(extracted_files)}"


# ==========================================
# FILE: worker.py
# ==========================================
import asyncio
import os
from temporalio.client import Client
from temporalio.worker import Worker

# Import Workflow and Activities
from workflows import DocumentTextExtractionWorkflow
from activities.file_ops import detect_mime_type, save_text_to_file, save_image_text_to_file
from activities.pdf_ops import extract_pdf_text_stream, extract_pdf_images
from activities.ocr_ops import perform_ocr_on_image_bytes

async def main():
    # Connect to Temporal Server
    temporal_server = os.getenv("TEMPORAL_ADDRESS", "localhost:7233")
    client = await Client.connect(temporal_server)

    # Register all separated activities
    all_activities = [
        detect_mime_type,
        save_text_to_file,
        save_image_text_to_file,
        extract_pdf_text_stream,
        extract_pdf_images,
        perform_ocr_on_image_bytes,
    ]

    # Run the worker
    worker = Worker(
        client,
        task_queue="doc-processing-queue",
        workflows=[DocumentTextExtractionWorkflow],
        activities=all_activities,
    )
    
    print(f"Worker started. Connecting to {temporal_server}, queuing 'doc-processing-queue'")
    await worker.run()

if __name__ == "__main__":
    asyncio.run(main())