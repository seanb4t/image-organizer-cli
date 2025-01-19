package main

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/dsoprea/go-exif/v3"
	"github.com/spf13/cobra"
	"golang.org/x/exp/slog"
)

func main() {
	var sourceDir, targetDir string
	var workers int

	rootCmd := &cobra.Command{
		Use:   "imagesorter",
		Short: "Sort images by metadata date into YYYY/MM directories",
		Run: func(cmd *cobra.Command, args []string) {
			if err := sortImages(sourceDir, targetDir, workers); err != nil {
				slog.Error("Error sorting images", slog.Any("error", err))
				os.Exit(1)
			}
		},
	}

	rootCmd.Flags().StringVarP(&sourceDir, "source", "s", "", "Source directory containing images (required)")
	rootCmd.Flags().StringVarP(&targetDir, "target", "t", "", "Target directory for sorted images (required)")
	rootCmd.Flags().IntVarP(&workers, "workers", "w", 4, "Number of parallel workers")
	rootCmd.MarkFlagRequired("source")
	rootCmd.MarkFlagRequired("target")

	if err := rootCmd.Execute(); err != nil {
		slog.Error("Failed to execute command", slog.Any("error", err))
		os.Exit(1)
	}
}

func sortImages(sourceDir, targetDir string, workers int) error {
	files, err := os.ReadDir(sourceDir)
	if err != nil {
		return fmt.Errorf("failed to read source directory: %w", err)
	}

	var wg sync.WaitGroup
	fileChan := make(chan string, workers)

	for i := 0; i < workers; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for file := range fileChan {
				if err := processFile(file, sourceDir, targetDir); err != nil {
					slog.Error("Failed to process file", slog.String("file", file), slog.Any("error", err))
				}
			}
		}()
	}

	for _, file := range files {
		if !file.IsDir() {
			fileChan <- file.Name()
		}
	}

	close(fileChan)
	wg.Wait()

	return nil
}

func processFile(fileName, sourceDir, targetDir string) error {
	sourcePath := filepath.Join(sourceDir, fileName)

	date, err := getImageDate(sourcePath)
	if err != nil {
		slog.Warn("Failed to extract metadata date, falling back to modification time", slog.String("file", sourcePath), slog.Any("error", err))
		fileInfo, err := os.Stat(sourcePath)
		if err != nil {
			return fmt.Errorf("failed to stat file: %w", err)
		}
		date = fileInfo.ModTime()
	}

	year := date.Format("2006")
	month := date.Format("01")

	targetPath := filepath.Join(targetDir, year, month)
	if err := os.MkdirAll(targetPath, os.ModePerm); err != nil {
		return fmt.Errorf("failed to create target directory: %w", err)
	}

	targetFile := filepath.Join(targetPath, fileName)
	if err := copyFile(sourcePath, targetFile); err != nil {
		return fmt.Errorf("failed to copy file: %w", err)
	}

	slog.Info("File processed", slog.String("source", sourcePath), slog.String("target", targetFile))
	return nil
}

func getImageDate(filePath string) (time.Time, error) {
	file, err := os.Open(filePath)
	if err != nil {
		return time.Time{}, fmt.Errorf("failed to open file: %w", err)
	}
	defer file.Close()

	context := exif.NewTagIndex()
	exifData, err := exif.Parse(file, context)
	if err != nil {
		return time.Time{}, fmt.Errorf("failed to parse EXIF data: %w", err)
	}

	timestamp, err := exifData.DateTime()
	if err != nil {
		return time.Time{}, fmt.Errorf("failed to get EXIF datetime: %w", err)
	}

	return timestamp, nil
}

func copyFile(src, dst string) error {
	sourceFile, err := os.Open(src)
	if err != nil {
		return err
	}
	defer sourceFile.Close()

	targetFile, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer targetFile.Close()

	if _, err := io.Copy(targetFile, sourceFile); err != nil {
		return err
	}

	return nil
}
