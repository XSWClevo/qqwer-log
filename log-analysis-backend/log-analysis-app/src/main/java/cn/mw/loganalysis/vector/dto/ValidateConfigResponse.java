package cn.mw.loganalysis.vector.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidateConfigResponse {
    private boolean valid;
    private String error;

    public static ValidateConfigResponse success() {
        return new ValidateConfigResponse(true, null);
    }

    public static ValidateConfigResponse fail(String error) {
        return new ValidateConfigResponse(false, error);
    }
}
