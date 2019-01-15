package at.fhtw.swe.model;

public class ValidationRequestBody {
    private String data;
    private String template;

    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(final String template) {
        this.template = template;
    }
}
