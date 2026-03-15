#!/bin/bash
echo "Creating S3 buckets..."
awslocal s3 mb s3://docmgr-raw-documents
awslocal s3 mb s3://docmgr-extracted-text
awslocal s3 mb s3://docmgr-extracted-images
echo "S3 buckets created."
