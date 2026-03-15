package demo.temporaio.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FileController {

    @GetMapping("/start-workflow")
    public Map<String, String> getFileData() {
        Map<String, String> data = new HashMap<>();

        // These keys must match your JavaScript data.fileType and data.size
        data.put("fileType", "image/png");
        data.put("size", "2.4 MB");

        return data;
    }
}
