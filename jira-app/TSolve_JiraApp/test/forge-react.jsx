// Stand-ins for the UI Kit components, which only render inside Jira. Tests check the page's behaviour with them;
// the real look is checked in `forge tunnel`.
import React from 'react';

const Pass = ({ children }) => <div>{children}</div>;

export const Box = Pass;
export const Stack = Pass;
export const Inline = Pass;
export const ButtonGroup = Pass;
// Icons are decoration here; a labelled one still gets its accessible name.
export const Icon = ({ label }) => (label ? <span role="img" aria-label={label} /> : null);
export const xcss = (style) => style;
export const Heading =({ children }) => <h2>{children}</h2>;
export const Text = ({ children }) => <p>{children}</p>;
export const Label = ({ labelFor, children }) => <label htmlFor={labelFor}>{children}</label>;
export const ErrorMessage = ({ children }) => <div role="alert">{children}</div>;
export const List = ({ children }) => <ul>{children}</ul>;
export const ListItem = ({ children }) => <li>{children}</li>;
export const Spinner = ({ label }) => <span role="progressbar" aria-label={label} />;
export const SectionMessage = ({ appearance, title, children }) => (
  <section data-appearance={appearance}>
    {title && <h3>{title}</h3>}
    {children}
  </section>
);
export const Button = ({ children, onClick, isDisabled, appearance }) => (
  <button type="button" onClick={onClick} disabled={isDisabled} data-appearance={appearance}>
    {children}
  </button>
);
export const TextArea = ({ id, value, onChange, isDisabled, isInvalid, placeholder }) => (
  <textarea
    id={id}
    value={value}
    onChange={onChange}
    disabled={isDisabled}
    aria-invalid={isInvalid}
    placeholder={placeholder}
  />
);

export const useProductContext = jest.fn();

export default { render: jest.fn() };
